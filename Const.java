/**
 * Created by alexhap on 22.02.2015.
 *
 */

class DisplayValue {
    private final String value;

    public DisplayValue(String value) {
        this.value = value;
    }

    public String toString() {
        return value;
    }
}

class Const {
    public static final byte B_HEADER = 0x16;
    public static final byte B_FINISH = 0x0D;

    public static final byte BR_OK = 0x01;
    public static final byte BR_PE = 0x06;

    public static final byte BIT_PRINT = 0x01;
    public static final byte BIT_PHOTO = 0x02;

    public static final byte B_COUNTER = 0x43; // 'C'
    public static final byte B_DELAY = 0x44; // 'D'
    public static final byte B_PRINT_END = 0x45; // 'E'
    public static final byte B_SELECT_MESSAGE_3 = 0x49; // 'I'
    public static final byte B_SELECT_MESSAGE_2 = 0x4D; // 'M'
    public static final byte B_START_PRINT = 0x50; // 'P'
    public static final byte B_STOP_PRINT = 0x51; // 'Q'
    public static final byte B_READ_STATUS = 0x53; // 'S'
    public static final byte B_SET_VARIABLE_2 = 0x54; // 'T'
    public static final byte B_SET_VARIABLE_1 = 0x56; // 'V'

    private static final DisplayValue S_HEADER_SEND = new DisplayValue("162A010250");
//    public static final DisplayValue S_HEADER_RECEIVE = new DisplayValue("163B");
    private static final DisplayValue S_ENDING = new DisplayValue("0D");

    private static String toString(byte b) {
        String res = Integer.toHexString(b);
        if (b < 0x10){
            return "0".concat(res).toUpperCase();
        } else {
            return res.toUpperCase();
        }
    }

    public static String strSend(byte b) {
        return S_HEADER_SEND.toString().concat(Const.toString(b).concat(S_ENDING.toString()));
    }
}
