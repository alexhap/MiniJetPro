/**
 * Created by alexhap on 09.01.2015.
 *
 */

import java.util.Arrays;
import java.util.Date;
import java.util.Observable;
import java.util.Observer;
import jssc.SerialPort;
import jssc.SerialPortException;

class ComCore extends Observable implements Observer{
    private static SerialPort serialPort;
    private static PortReader portReader;
    private boolean stopFlag; // MiniJetPro duplicates last returned "print-end flag" in answer on STOP_PRINT command.
    private byte[] fragment;  // array for manipulating with fragmented packets
    private String commandBuffer; // command to execute after layout selection
    private int toPrintObjects;
    private int printedObjects;
    private int toPrintLabels;
    private int printedLabels;
    private int layerSelectDelay;
    private boolean printActive;
    private boolean debugLog;
    private boolean paused;

    public ComCore(String portName, int layerDelay) {
        stopFlag = false;
        printActive = false;
        debugLog = false;
        commandBuffer = "";
        portReader = null;
        layerSelectDelay = layerDelay;
        openPort(portName);
    }

    public void setPause(boolean value) {
        paused = value;
        if (!paused && printActive) sendData(0, "", 0, 0, Const.strSend(Const.B_START_PRINT));
    }

    public boolean isPrintActive() {
        return printActive;
    }

    public int getPrintedObjects() {
        return printedObjects;
    }

    public int getPrintedLabels() {
        return printedLabels;
    }

    public int getLeftToPrint() {
        if (toPrintObjects - printedObjects > 0) {
            return toPrintObjects - printedObjects;
        } else {
            return 0;
        }
    }

    public boolean isPortOpened() {
        return serialPort != null && serialPort.isOpened();
    }

    public void sendData(int layout, String artikul, int objCount, int labelCount, String command) {
        try {
            if (objCount > 0) {
                toPrintObjects = objCount;
                printedObjects = 0;
                toPrintLabels = labelCount;
                printedLabels = 0;
                if (layout == -1 || command.equals(Const.strSend(Const.B_READ_STATUS))) {
                    if (debugLog) toLog(String.format("=> Отправка команды (%s)", command));
                    serialPort.writeBytes(strToHex(command));
                    commandBuffer = "";
                } else {
                    commandBuffer = command;
                    String strSend;
                    if (layout < 100) {
                        strSend = Const.strSend(Const.B_SELECT_MESSAGE_2, layout, 2);
                    } else {
                        strSend = Const.strSend(Const.B_SELECT_MESSAGE_3, layout, 3);
                    }
                    serialPort.writeBytes(strToHex(strSend));
                    String str = String.format("=> Шаблон: %d, Кол-во: %d по %d, Артикул: %s", layout, objCount, labelCount, artikul);
                    if (debugLog) str = str.concat(String.format(", команда: %s", strSend));
                    toLog(str);
                }
            } else {
                serialPort.writeBytes(strToHex(command));
                toLog(String.format("=> %s", command));
            }
        } catch (SerialPortException ex) {
//            ex.printStackTrace();
            toLog(String.format("!! Ошибка COM порта при отправке команды (%s-%s)", ex.getMessage(), ex.getMethodName()));
            printActive = false;
        }
    }

    private void receiveData(byte[] buffer) {
        if (buffer[0] != Const.B_HEADER) { // bad packet
            if (debugLog) toLog(String.format("** bad/fragmented packet: %s. (buffer[0] != Const.B_HEADER)", hexToStr(buffer)));
            if (fragment.length > 0) {
                if (debugLog) toLog(String.format("** fragmented packet: %s. (fragment.length > 0)", hexToStr(fragment)));
                byte[] bufBuf = Arrays.copyOf(fragment, fragment.length + buffer.length);
                System.arraycopy(buffer, 0, bufBuf, fragment.length, buffer.length);
                if (debugLog) toLog(String.format("** restored packet: %s. (a+b)", hexToStr(bufBuf)));
                if (bufBuf.length < 6) { // one more time
                    fragment = bufBuf.clone();
                    if (debugLog) toLog(String.format("** packet still not good: %s. (bufBuf.length < 6)", hexToStr(fragment)));
                } else { // packet restored
                    if (debugLog) toLog(String.format("** packet restored normally: %s. (bufBuf.length >= 6)", hexToStr(bufBuf)));
                    fragment = Arrays.copyOf(bufBuf, 0);
                    receiveData(bufBuf);
                }
            } else {
                toLog(String.format("!! Ошибка передачи данных. Сброс испорченного пакета: %s", hexToStr(buffer)));
            }
        } else { // good packet
            if (debugLog) toLog(String.format("** good packet: %s. (buffer[0] == Const.B_HEADER)", hexToStr(buffer)));
            if (buffer.length > 6) { // overflow
                if (debugLog) toLog(String.format("** good overflowed packet #1: %s. (buffer.length > 6)", hexToStr(Arrays.copyOf(buffer, 6))));
                receiveData(Arrays.copyOf(buffer, 6));
                if (debugLog) toLog(String.format("** good overflowed packet #2: %s. (buffer.length > 6)", hexToStr(Arrays.copyOfRange(buffer, 6, buffer.length))));
                receiveData(Arrays.copyOfRange(buffer, 6, buffer.length));
            } else if (buffer.length < 6) { // fragmentation
                if (debugLog) toLog(String.format("** good fragmented packet stored: %s. (buffer.length < 6)", hexToStr(buffer)));
                if (fragment.length > 0) {
                    toLog(String.format("!! Ошибка передачи данных. Старый фрагментированный пакет считается испорченным, сброс: %s", hexToStr(fragment)));
                }
                fragment = buffer.clone();
            } else if (buffer.length == 6 && buffer[5] == Const.B_FINISH) { // full one packet
                if (debugLog) toLog(String.format("** good packet to analyze: %s. (buffer.length == 6)", hexToStr(buffer)));
                String strDebug = String.format("<= %s: ", hexToStr(buffer));
                String strText;
                String strOk = "выполнено.";
                String strFail = "не ";
                byte commandAtEnd = Const.B_EMPTY;
                if (buffer[2] == Const.B_COUNTER) {
                    strText = "Установка счетчика: ";
                    if (buffer[4] != Const.BR_OK) {
                        strText = strText.concat(strFail);
                    }
                } else if (buffer[2] == Const.B_DELAY) {
                    strText = "Установка задержки печати: ";
                    if (buffer[4] != Const.BR_OK) {
                        strText = strText.concat(strFail);
                    }
                } else if (buffer[2] == Const.B_PRINT_END) {
                    if (!stopFlag) {
                        strText = "Печать макета: ";
                        if (buffer[4] != Const.BR_OK || buffer[3] != Const.BR_PrintEnd) {
                            strText = strText.concat(strFail);
                        } else {
                            printedLabels++;
                            if (printedLabels >= toPrintLabels) {
                                printedLabels = 0;
                                printedObjects++;
                            }
                            if (paused && printedLabels == 0) {
                                strText = "";
                                strOk = String.format("!! Печать выполнена %d раз. Временная приостановка печати.", printedObjects);
                                commandAtEnd = Const.B_STOP_PRINT;
                                stopFlag = true;
                            } else if (printedObjects >= toPrintObjects) {
                                if (debugLog) {
                                    strOk = strOk.concat(String.format("\n!! Печать выполнена %d раз. Остановка печати.", printedObjects));
                                } else {
                                    strText = "";
                                    strOk = String.format("!! Печать выполнена %d раз. Остановка печати.", printedObjects);
                                }
                                commandAtEnd = Const.B_STOP_PRINT;
                                stopFlag = true;
                            }
                        }
                    } else {
                        strText = "Избыточный пакет данных, пропуск: ";
                    }
                } else if (buffer[2] == Const.B_SELECT_MESSAGE_2 || buffer[2] == Const.B_SELECT_MESSAGE_3) {
                    strText = "Выбор макета для печати: ";
                    if (buffer[4] != Const.BR_OK) {
                        strText = strText.concat(strFail);
                    } else {
                        commandAtEnd = Const.B_SET_VARIABLE_1;
                    }
                } else if (buffer[2] == Const.B_START_PRINT) {
                    strText = "Включение режима печати: ";
                    if (buffer[4] != Const.BR_OK) {
                        strText = strText.concat(strFail);
                    } else {
                        strOk = strOk.concat(" (ON)");
                        printActive = true;
                        stopFlag = false;
                    }
                } else if (buffer[2] == Const.B_STOP_PRINT) {
                    strText = "Выключение режима печати: ";
                    if (buffer[4] != Const.BR_OK) {
                        strText = strText.concat(strFail);
                    } else {
                        if (paused) {
                            strText = strText.concat("(ПАУЗА): ");
                        } else {
                            strOk = strOk.concat(" (OFF)");
                            printActive = false;
                            stopFlag = false;
                        }
                    }
                } else if (buffer[2] == Const.B_READ_STATUS) {
                    strText = "Запрос статуса: ";
                    if (buffer[4] != Const.BR_OK) {
                        strText = strText.concat(strFail);
                    } else {
                        String tmp = " : Печать включена: ";
                        if ((buffer[3] & Const.BIT_PRINT) == Const.BIT_PRINT) {
                            strOk = strOk.concat(tmp).concat("ДА");
                        } else {
                            strOk = strOk.concat(tmp).concat("НЕТ");
                        }
                        tmp = ", Фотодатчик: ";
                        if ((buffer[3] & Const.BIT_PHOTO) == Const.BIT_PHOTO) {
                            strOk = strOk.concat(tmp).concat("ДА");
                        } else {
                            strOk = strOk.concat(tmp).concat("НЕТ");
                        }
                    }
                } else if (buffer[2] == Const.B_SET_VARIABLE_1 || buffer[2] == Const.B_SET_VARIABLE_2) {
                    strText = "Установка значения переменной: ";
                    if (buffer[4] != Const.BR_OK) {
                        strText = strText.concat(strFail);
                    } else {
                        commandAtEnd = Const.B_START_PRINT;
                    }
                } else {
                    strText = "Пакет данных не распознан.";
                    strOk = "";
                }
                if (debugLog || buffer[2] != Const.B_PRINT_END || commandAtEnd == Const.B_STOP_PRINT) {
                    toLog(strDebug.concat(strText).concat(strOk));
                }
                if (commandAtEnd == Const.B_SET_VARIABLE_1) {
                    try {
                        Thread.sleep(layerSelectDelay);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    sendData(-1, "", toPrintObjects, toPrintLabels, commandBuffer);
                } else if (commandAtEnd != Const.B_EMPTY) {
                    sendData(0, "", 0, 0, Const.strSend(commandAtEnd));
                }
            } else {
                toLog(String.format("<= %s: пакет данных не распознан.", hexToStr(buffer)));
            }
        }
    }

    public void closePort() {
        try {
            if (serialPort != null) {
                if (serialPort.isOpened()) {
                    toLog(String.format("-- Соединение по порту %s завершено.", serialPort.getPortName()));
                    serialPort.removeEventListener();
                    serialPort.closePort();
                }
            }
        } catch (SerialPortException ex) {
            ex.printStackTrace();
        }
    }

    public void openPort(String portName) {
        if (serialPort != null && serialPort.isOpened() && !portName.equals(serialPort.getPortName()))
            closePort();
        try {
            if (serialPort == null) {
                serialPort = new SerialPort(portName);
                portReader = new PortReader(serialPort);
                portReader.addObserver(this);
            }
            if (!serialPort.isOpened()) {
                serialPort.openPort();
                serialPort.setParams(SerialPort.BAUDRATE_38400, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
                serialPort.setFlowControlMode(SerialPort.FLOWCONTROL_XONXOFF_IN | SerialPort.FLOWCONTROL_XONXOFF_OUT);
                serialPort.addEventListener(portReader, SerialPort.MASK_RXCHAR);
                toLog(String.format("-- Соединение по порту %s установлено.", portName));
            }
        } catch (SerialPortException ex) {
            ex.printStackTrace();
        }
    }

    public void setDebugLog(boolean mode) {
        debugLog = mode;
    }

    public void update(Observable pr, Object arg) {
        receiveData((byte[]) arg);
    }

    private void toLog(String str) {
        java.util.Date cDate = new Date();
        String strDate;
        if (str.startsWith("--")) {
            strDate = String.format("%tF %tT ", cDate, cDate);
        } else {
            strDate = String.format("%tT ", cDate);
        }
        setChanged();
        notifyObservers(strDate.concat(str).concat("\n"));
    }

    private byte[] strToHex(String str) {
        if ((str.length() & 1) == 1) str = "0" + str;
        int size = str.length() / 2;
        byte[] res = new byte[size];
        for (int i = 0; i < size; i++)
            res[i] = Integer.valueOf(str.substring(i * 2, i * 2 + 2), 16).byteValue();
        return res;
    }

    private String hexToStr(byte[] arr) {
        String tmp, res = "";
        for (byte member : arr) {
            tmp = Integer.toHexString(Byte.toUnsignedInt(member)).toUpperCase();
            if (tmp.length() < 2) {
                tmp = "00".concat(tmp);
                tmp = tmp.substring(tmp.length() - 2, tmp.length());
            }
            res = res.concat(tmp);
        }
        return res;
    }
}
