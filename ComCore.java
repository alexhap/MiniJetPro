/**
 * Created by alexhap on 09.01.2015.
 *
 */

import java.util.Arrays;
import java.util.Observable;
import java.util.Observer;
import jssc.SerialPort;
import jssc.SerialPortException;

class ComCore extends Observable implements Observer{
    private static SerialPort serialPort;
    private boolean StopFlag; // MiniJetPro duplicates last returned "print-end flag" in answer on STOP_PRINT command.
    private byte[] fragment;  // array for manipulating with fragmented packets
    private String commandBuffer; // command to execute after layout selection
    private int toPrintObjects;
    private int PrintedObjects;
    private int toPrintLabels;
    private int PrintedLabels;
    private boolean PrintActive;
    private boolean DebugLog;

    public ComCore(String Port) {
        serialPort = new SerialPort(Port);
        StopFlag = false;
        PrintActive = false;
        DebugLog = false;
        commandBuffer = "";
        try {
            serialPort.openPort();
            serialPort.setParams(SerialPort.BAUDRATE_38400, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
            serialPort.setFlowControlMode(SerialPort.FLOWCONTROL_XONXOFF_IN | SerialPort.FLOWCONTROL_XONXOFF_OUT);
            PortReader portReader = new PortReader(serialPort);
            serialPort.addEventListener(portReader, SerialPort.MASK_RXCHAR);
            portReader.addObserver(this);
            this.SendData(0, "", 0, 0, Const.strSend(Const.B_READ_STATUS));
        } catch (SerialPortException ex) {
            this.toLog("Не удалось подключиться по порту ".concat(Port));
        }
    }

    public boolean isPrintActive() {
        return PrintActive;
    }

    public int getPrintedObjects() {
        return PrintedObjects;
    }

    public int getPrintedLabels() {
        return PrintedLabels;
    }

    public int getLeftToPrint() {
        if (toPrintObjects - PrintedObjects > 0) {
            return toPrintObjects - PrintedObjects;
        } else {
            return 0;
        }
    }

    public boolean isPortOpened() {
        return serialPort.isOpened();
    }

    public void SendData(int layout, String artikul, int objCount, int labelCount, String command) {
        try {
            if (objCount > 0) {
                toPrintObjects = objCount;
                PrintedObjects = 0;
                toPrintLabels = labelCount;
                PrintedLabels = 0;
                if (layout == -1) {
                    if (DebugLog) toLog(String.format("=> Отправка команды после выбора макета: (%s)", command));
                    serialPort.writeBytes(StrToHex(command));
                    commandBuffer = "";
                } else {
                    commandBuffer = command;
                    String strSend;
                    if (layout < 100) {
                        strSend = Const.strSend(Const.B_SELECT_MESSAGE_2, layout, 2);
                    } else {
                        strSend = Const.strSend(Const.B_SELECT_MESSAGE_3, layout, 3);
                    }
                    serialPort.writeBytes(StrToHex(strSend));
                    toLog(String.format("=> L=%d, %d*%d %s (%s)", layout, objCount, labelCount, artikul, strSend));
                }
            } else {
                serialPort.writeBytes(StrToHex(command));
                toLog(String.format("=> %s", command));
            }
        } catch (SerialPortException ex) {
            toLog(String.format("!! Ошибка COM порта при отправке команды (%s)", command));
            PrintActive = false;
        }
    }

    private void ReceiveData(byte[] buffer) {
        if (buffer[0] != Const.B_HEADER) { // bad packet
            if (DebugLog) toLog(String.format("** bad/fragmented packet: %s. (buffer[0] != Const.B_HEADER)", HexToStr(buffer)));
            if (fragment.length > 0) {
                if (DebugLog) toLog(String.format("** fragmented packet: %s. (fragment.length > 0)", HexToStr(fragment)));
                byte[] bufBuf = Arrays.copyOf(fragment, fragment.length + buffer.length);
                System.arraycopy(buffer, 0, bufBuf, fragment.length, buffer.length);
                if (DebugLog) toLog(String.format("** restored packet: %s. (a+b)", HexToStr(bufBuf)));
                if (bufBuf.length < 6) { // one more time
                    fragment = bufBuf.clone();
                    if (DebugLog) toLog(String.format("** packet still not good: %s. (bufBuf.length < 6)", HexToStr(fragment)));
                } else { // packet restored
                    if (DebugLog) toLog(String.format("** packet restored normally: %s. (bufBuf.length >= 6)", HexToStr(bufBuf)));
                    fragment = Arrays.copyOf(bufBuf, 0);
                    ReceiveData(bufBuf);
                }
            } else {
                toLog(String.format("!! Ошибка передачи данных. Сброс испорченного пакета: %s", HexToStr(buffer)));
            }
        } else { // good packet
            if (DebugLog) toLog(String.format("** good packet: %s. (buffer[0] == Const.B_HEADER)", HexToStr(buffer)));
            if (buffer.length > 6) { // overflow
                if (DebugLog) toLog(String.format("** good overflowed packet #1: %s. (buffer.length > 6)", HexToStr(Arrays.copyOf(buffer, 6))));
                ReceiveData(Arrays.copyOf(buffer, 6));
                if (DebugLog) toLog(String.format("** good overflowed packet #2: %s. (buffer.length > 6)", HexToStr(Arrays.copyOfRange(buffer, 6, buffer.length))));
                ReceiveData(Arrays.copyOfRange(buffer, 6, buffer.length));
            } else if (buffer.length < 6) { // fragmentation
                if (DebugLog) toLog(String.format("** good fragmented packet stored: %s. (buffer.length < 6)", HexToStr(buffer)));
                if (fragment.length > 0) {
                    toLog(String.format("!! Ошибка передачи данных. Старый фрагментированный пакет считается испорченным, сброс: %s", HexToStr(fragment)));
                }
                fragment = buffer.clone();
            } else if (buffer.length == 6 && buffer[5] == Const.B_FINISH) { // full one packet
                if (DebugLog) toLog(String.format("** good packet to analyze: %s. (buffer.length == 6)", HexToStr(buffer)));
                String strDebug = String.format("<= %s: ", HexToStr(buffer));
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
                    if (!StopFlag) {
                        strText = "Печать макета: ";
                        if (buffer[4] != Const.BR_OK || buffer[3] != Const.BR_PE) {
                            strText = strText.concat(strFail);
                        } else {
                            PrintedLabels++;
                            if (PrintedLabels >= toPrintLabels) {
                                PrintedLabels = 0;
                                PrintedObjects++;
                            }
                            if (PrintedObjects >= toPrintObjects) {
                                if (DebugLog) {
                                    strOk = strOk.concat(String.format("\n!! Печать выполнена %d раз. Остановка печати.", PrintedObjects));
                                } else {
                                    strText = "";
                                    strOk = String.format("!! Печать выполнена %d раз. Остановка печати.", PrintedObjects);
                                }
                                commandAtEnd = Const.B_STOP_PRINT;
                                StopFlag = true;
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
                        PrintActive = true;
                        StopFlag = false;
                    }
                } else if (buffer[2] == Const.B_STOP_PRINT) {
                    strText = "Выключение режима печати: ";
                    if (buffer[4] != Const.BR_OK) {
                        strText = strText.concat(strFail);
                    } else {
                        strOk = strOk.concat(" (OFF)");
                        PrintActive = false;
                        StopFlag = false;
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
                if (DebugLog || buffer[2] != Const.B_PRINT_END || commandAtEnd == Const.B_STOP_PRINT) {
                    toLog(strDebug.concat(strText).concat(strOk));
                }
                if (commandAtEnd == Const.B_SET_VARIABLE_1) {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    SendData(-1, "", toPrintObjects, toPrintLabels, commandBuffer);
                } else if (commandAtEnd != Const.B_EMPTY) {
                    SendData(0, "", 0, 0, Const.strSend(commandAtEnd));
                }
            } else {
                toLog(String.format("<= %s: пакет данных не распознан.", HexToStr(buffer)));
            }
        }
    }

    public void ClosePort() {
        try {
            serialPort.closePort();
        } catch (SerialPortException e) {
            e.printStackTrace();
        }
    }

    public void setDebugLog(boolean mode) {
        DebugLog = mode;
    }

    public void update(Observable pr, Object arg) {
        ReceiveData((byte[]) arg);
    }

    private void toLog(String str) {
        setChanged();
        notifyObservers(str.concat("\n"));
    }

    private byte[] StrToHex(String str) {
        if ((str.length() & 1) == 1) str = "0" + str;
        int size = str.length() / 2;
        byte[] res = new byte[size];
        for (int i = 0; i < size; i++)
            res[i] = Integer.valueOf(str.substring(i * 2, i * 2 + 2), 16).byteValue();
        return res;
    }

    private String HexToStr(byte[] arr) {
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
