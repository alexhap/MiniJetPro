/**
 * Created by alexhap on 18.02.2015.
 *
 */

import java.util.Observable;
import jssc.SerialPort;
import jssc.SerialPortEvent;
import jssc.SerialPortEventListener;
import jssc.SerialPortException;

class PortReader extends Observable implements SerialPortEventListener {
    private final SerialPort serialPort;

    public PortReader(SerialPort sp) {
        serialPort = sp;
    }

    public void serialEvent(SerialPortEvent event) {
        try {
            Thread.sleep(20);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (event.getEventValue() > 0) {
            try {
                notifyObservers(serialPort.readBytes());
                this.setChanged();
            } catch (SerialPortException e) {
                e.printStackTrace();
            }
        }
    }
}

