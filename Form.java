/**
 * Created by alexhap on 18.02.2015.
 *
 */

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.awt.Dimension;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import javax.swing.*;
import javax.swing.Timer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumnModel;
import javax.swing.text.DefaultCaret;
import org.ini4j.Ini;

class Form extends JFrame implements WindowListener, Observer {
    private JPanel panelMain;
    private JPanel panel1;
    private JPanel panel2;
    private JPanel panel3;
    private JPanel panel11;
    private JPanel panel12;
    private JPanel panel13;
    private JLabel lStatus1;
    private JLabel lStatus2;
    private JLabel lStatus3;
    private JLabel lStatus4;
    private JComboBox cbPrinterPort;
    private JTextField tFolderToMonitor;
    private JCheckBox cbActive;
    private JTextField tCommand;
    private JButton bFolderChoose;
    private JButton bSendTest;
    private JButton bLoadTasks;
    private JButton bSendActiveString;
    private JButton bSendAll;
    private JButton bStop;
    private JButton bClearLog;
    private JCheckBox cbSaveLogToFile;
    private JCheckBox cbDebugLog;
    private JTable tTasks;
    private JTextArea textLog;
    private JSpinner spObjectCount;
    private JSpinner spLabelsPerObject;
    private JSpinner spLayout;
    private final Timer timerMonitor;
    private final Timer timerSend;
    private final Timer timerStatus;

    private boolean RowSendComplete;
    private boolean TableSendComplete;
    private int CurrentRow;
    private static ComCore cc = null;

    @Override
    public void windowOpened(WindowEvent e) {
        LoadSettings();
    }

    @Override
    public void windowClosing(WindowEvent e) {
        SaveSettings();
        SaveLogToFile();
        if (cc != null && cc.isPortOpened()) {
            cc.ClosePort();
        }
    }

    @Override
    public void windowClosed(WindowEvent e) {
    }

    @Override
    public void windowIconified(WindowEvent e) {
    }

    @Override
    public void windowDeiconified(WindowEvent e) {
    }

    @Override
    public void windowActivated(WindowEvent e) {
    }

    @Override
    public void windowDeactivated(WindowEvent e) {
    }

    private Form() {
        this.addWindowListener(this);

        bFolderChoose.addActionListener(e -> onFolderChooseAction());
        bSendTest.addActionListener(e -> onSendTestAction());
        bLoadTasks.addActionListener(e -> onLoadTaskAction());
        bSendActiveString.addActionListener(e -> onSendActiveRowAction());
        bSendAll.addActionListener(e -> onSendAllAction());
        bStop.addActionListener(e -> onStopAction());
        bClearLog.addActionListener(e -> onClearLogAction());
        spObjectCount.addChangeListener(e -> onObjectCountChange());
        spLabelsPerObject.addChangeListener(e -> onLabelsPerObjectChange());
        spLayout.addChangeListener(e -> onLayoutChange());
        cbPrinterPort.addActionListener(e -> onPrinterPortChange());
        cbActive.addActionListener(e -> onCheckActiveAction());
        timerSend = new Timer(100, e -> timerSendAction());
        timerMonitor = new Timer(500, e -> timerMonitorAction());
        timerStatus = new Timer(333, e -> timerStatusAction());

        tTasks.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = ((JTable) e.getSource()).rowAtPoint(e.getPoint());
                    spLayout.setValue(Integer.valueOf((String) tTasks.getModel().getValueAt(row, 0)));
                    spObjectCount.setValue(Integer.valueOf((String) tTasks.getModel().getValueAt(row, 2)));
                    spLabelsPerObject.setValue(Integer.valueOf((String) tTasks.getModel().getValueAt(row, 3)));
                    tCommand.setText((String) tTasks.getModel().getValueAt(row, 4));
                }
                super.mouseClicked(e);
            }
        });

        tTasks.setFont(tTasks.getFont().deriveFont(NORMAL, 12));
        textLog.setFont(textLog.getFont().deriveFont(NORMAL, 12));
        ((DefaultCaret) textLog.getCaret()).setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);

        RowSendComplete = true;
        TableSendComplete = true;
        CurrentRow = 0;
        setMinimumSize(new Dimension(720, 400));
        setTitle("MiniJetPro printer control");
    }

    private void onFolderChooseAction() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Выберите папку для мониторинга заданий");
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (!tFolderToMonitor.getText().equals("")) {
            File tmpFile = new File(tFolderToMonitor.getText());
            if (tmpFile.exists()) {
                fc.setCurrentDirectory(tmpFile.getAbsoluteFile());
            }
        }
        if (fc.showOpenDialog(bFolderChoose) == JFileChooser.APPROVE_OPTION) {
            tFolderToMonitor.setText(fc.getSelectedFile().getAbsolutePath());
        }
    }

    private void onSendTestAction() {
        SendCommand((int) spLayout.getValue(), "Тестовая строка", (int) spObjectCount.getValue(),
                (int) spLabelsPerObject.getValue(), tCommand.getText());
    }

    private void onLoadTaskAction() {
        LoadTasks("", true);
    }

    private void onSendActiveRowAction() {
        if (tTasks.getRowCount() > 0 && tTasks.getSelectedRowCount() > 0) {
            SendTableRow(tTasks.getSelectedRow());
        }
    }

    private void onSendAllAction() {
        SendTable();
    }

    private void onStopAction() {
        timerMonitor.stop();
        timerSend.stop();
        RowSendComplete = true;
        TableSendComplete = true;
        SendCommand(0, "", 0, 0, Const.strSend(Const.B_STOP_PRINT));
    }

    private void onClearLogAction() {
        SaveLogToFile();
        textLog.setText("");
    }

    private void onObjectCountChange() {
        if ((int) spObjectCount.getValue() < 1) {
            spObjectCount.setValue(1);
        }
    }

    private void onLabelsPerObjectChange() {
        if ((int) spLabelsPerObject.getValue() < 1) {
            spLabelsPerObject.setValue(1);
        }
    }

    private void onLayoutChange() {
        if ((int) spLayout.getValue() < 0) {
            spLayout.setValue(0);
        } else if ((int) spLayout.getValue() > 999) {
            spLayout.setValue(999);
        }
    }

    private void onPrinterPortChange() {
        if (cc != null && cc.isPortOpened()) {
            cc.deleteObservers();
            cc.ClosePort();
        }
        cc = new ComCore(cbPrinterPort.getSelectedItem().toString());
        cc.addObserver(this);
    }

    private void onCheckActiveAction() {
        if (cbActive.getModel().isSelected()) {
            File path = new File(tFolderToMonitor.getText());
            if (path.exists()) {
                timerMonitor.start();
            } else {
                cbActive.getModel().setSelected(timerMonitor.isRunning());
            }
        } else {
            timerMonitor.stop();
        }
    }

    private void timerSendAction() {
        if (RowSendComplete) {
            if (CurrentRow < tTasks.getRowCount()) {
                SendTableRow(CurrentRow++);
            } else {
                TableSendComplete = true;
                timerSend.stop();
            }
        }
    }

    private void timerMonitorAction() {
        if (TableSendComplete) {
            File path = new File(tFolderToMonitor.getText());
            String[] files = path.list((dir, name) -> name.endsWith(".txt"));
            if (files.length > 0) {
                File destinationFile = new File(path.getAbsolutePath().concat(File.separator).concat(files[0]).concat(".old"));
                path = new File(path.getAbsolutePath().concat(File.separator).concat(files[0]));
                if (path.renameTo(destinationFile)) {
                    if (LoadTasks(destinationFile.getAbsolutePath(), false)) {
                        SendTable();
                    }
                } else {
                    textLog.append(String.format("Не удалось переименовать файл %s, мониторинг остановлен.\n", path.getName()));
                    timerMonitor.stop();
                    cbActive.getModel().setSelected(false);
                }
            }
        }
    }

    private void timerStatusAction() {
        if (cc != null && cc.isPortOpened()) {
            if (cc.isPrintActive()) {
                lStatus1.setText("Печать ...");
            } else {
                lStatus1.setText("Остановлен");
            }
            lStatus2.setText(Integer.toString(cc.getPrintedObjects()));
            lStatus3.setText(Integer.toString(cc.getPrintedLabels()));
            lStatus4.setText(Integer.toString(cc.getLeftToPrint()));
        } else {
            lStatus1.setText("Недоступен");
            lStatus2.setText("-");
            lStatus3.setText("-");
            lStatus4.setText("-");
        }
    }

    private void SendCommand(int layout, String artikul, int objCount, int labelCount, String command) {
        RowSendComplete = false;
        cc.setDebugLog(cbDebugLog.getModel().isSelected());
        cc.SendData(layout, artikul, objCount, labelCount, command);
    }

    private void SendTableRow(int row) {
        SendCommand(Integer.valueOf((String) tTasks.getModel().getValueAt(row, 0)), // Layout to use
                                    (String) tTasks.getModel().getValueAt(row, 1),  // user friendly label name
                    Integer.valueOf((String) tTasks.getModel().getValueAt(row, 2)), // objects to print
                    Integer.valueOf((String) tTasks.getModel().getValueAt(row, 3)), // labels per object
                                    (String) tTasks.getModel().getValueAt(row, 4)); // printer command
        if (row < tTasks.getRowCount() - 1) {
            tTasks.setRowSelectionInterval(row + 1, row + 1);
        } else {
            tTasks.removeRowSelectionInterval(row, row);
        }
    }

    private void SendTable() {
        if (tTasks.getRowCount() > 0) {
            TableSendComplete = false;
            tTasks.setRowSelectionInterval(0, 0);
            CurrentRow = 0;
            timerSend.start();
        }
    }

    private boolean LoadTasks(String fileToLoad, boolean manual) {
        boolean result = false;
        if (manual) {
            JFileChooser fc = new JFileChooser(tFolderToMonitor.getText());
            fc.setDialogTitle("Выберите файл заданий для принтера");
            fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                fileToLoad = fc.getSelectedFile().getAbsolutePath();
            }
        }
        File fLoad = new File(fileToLoad);
        if (fLoad.exists()) {
            DefaultTableModel tm = (DefaultTableModel) tTasks.getModel();
            while (tm.getRowCount() > 0) {
                tm.removeRow(0);
            }
            tm.setColumnCount(0);
            tm.addColumn("Шаблон");
            tm.addColumn("Артикул");
            tm.addColumn("Кол-во заготовок");
            tm.addColumn("Кол-во меток");
            tm.addColumn("Команда для принтера");
            try {
                BufferedReader br = new BufferedReader(new FileReader(fLoad));
                String str;
                String[] arrStr;
                int index = 0;
                while ((str = br.readLine()) != null) {
                    index++;
                    arrStr = str.split(";");
                    if (arrStr.length == 5) {
                        Vector<String> row = new Vector<>();
                        row.add(arrStr[0].trim()); // layout #
                        row.add(arrStr[1].trim()); // user friendly label name
                        row.add(arrStr[2].trim()); // objects to print count
                        row.add(arrStr[3].trim()); // labels per object count
                        row.add(arrStr[4].trim().toUpperCase()); // printer command
                        tm.addRow(row);
                    } else {
                        textLog.append(String.format("Ошибка данных в строке №%d: %s\n", index, str));
                    }
                }
                br.close();
                TableColumnModel tcm = tTasks.getColumnModel();
                tcm.getColumn(0).setWidth(60);
                tcm.getColumn(0).setMinWidth(60);
                tcm.getColumn(0).setMaxWidth(100);
                tcm.getColumn(1).setWidth(150);
                tcm.getColumn(1).setMinWidth(100);
                tcm.getColumn(2).setWidth(60);
                tcm.getColumn(2).setMinWidth(60);
                tcm.getColumn(2).setMaxWidth(100);
                tcm.getColumn(3).setWidth(60);
                tcm.getColumn(3).setMinWidth(60);
                tcm.getColumn(3).setMaxWidth(100);
                tcm.getColumn(4).setWidth(200);
                tcm.getColumn(4).setMinWidth(200);
                tTasks.setDefaultEditor(tTasks.getColumnClass(0), null);
                tTasks.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
                if (tTasks.getRowCount() > 0) {
                    textLog.append(String.format("Загружены задания из файла: %s\n", fLoad.getName()));
                    result = true;
                } else {
                    textLog.append(String.format("В файле %s не найдено заданий.\n", fLoad.getName()));
                }
            } catch (IOException e) {
                textLog.append(String.format("Ошибка чтения (открытия) файла %s.", fileToLoad));
            }
        } else if (!manual) {
            textLog.append(String.format("Файл не найден: %s", fileToLoad));
        }
        return result;
    }

    public void update(Observable pr, Object arg) {
        String str = (String) arg;
        textLog.append(str);
        if (str.endsWith("(ON)\n")) {
            SetInterface(false);
            timerStatus.start();
        } else if (str.endsWith("(OFF)\n")) {
            SetInterface(true);
            timerStatus.stop();
            timerStatusAction();
            RowSendComplete = true;
        }
    }

    private void SetInterface(boolean mode) {
        cbPrinterPort.setEnabled(mode);
        tFolderToMonitor.setEnabled(mode);
        bFolderChoose.setEnabled(mode);
        cbActive.setEnabled(mode);
        tCommand.setEnabled(mode);
        spObjectCount.setEnabled(mode);
        bLoadTasks.setEnabled(mode);
        bSendActiveString.setEnabled(mode);
        bSendAll.setEnabled(mode);
        bSendTest.setEnabled(mode);
    }

    private void LoadSettings() {
        Ini prefs;
        try {
            prefs = new Ini(new File("Config.ini"));
        } catch (IOException e) {
            prefs = new Ini();
            prefs.put("MiniJetPro", "PrinterPort", 0);
            prefs.put("MiniJetPro", "FolderToMonitor", "");
            prefs.put("MiniJetPro", "ActiveMonitoring", false);
            prefs.put("MiniJetPro", "Command", "162A010250530D");
            prefs.put("MiniJetPro", "ObjectCount", 1);
            prefs.put("MiniJetPro", "LabelsPerObject", 1);
            prefs.put("MiniJetPro", "PrintLayout", 1);
            prefs.put("MiniJetPro", "SaveLog", false);
            prefs.put("MiniJetPro", "WinXPos", 1);
            prefs.put("MiniJetPro", "WinYPos", 1);
            prefs.put("MiniJetPro", "WinXSize", 720);
            prefs.put("MiniJetPro", "WinYSize", 400);
        }
        if (prefs.get("MiniJetPro", "PrinterPort", Integer.class) != null) {
            cbPrinterPort.setSelectedIndex(prefs.get("MiniJetPro", "PrinterPort", Integer.class));
        }
        String folder = prefs.get("MiniJetPro", "FolderToMonitor", String.class);
        if (!folder.equals("")) {
            File f = new File(folder);
            if (f.exists()) {
                tFolderToMonitor.setText(folder);
                if (prefs.get("MiniJetPro", "ActiveMonitoring", Boolean.class) != null) {
                    cbActive.setSelected(prefs.get("MiniJetPro", "ActiveMonitoring", Boolean.class));
                    if (cbActive.getModel().isSelected()) {
                        timerMonitor.start();
                    }
                }
            }
        }
        if (prefs.get("MiniJetPro", "Command", String.class) != null) {
            tCommand.setText(prefs.get("MiniJetPro", "Command", String.class));
        }
        if (prefs.get("MiniJetPro", "ObjectCount", Integer.class) != null) {
            spObjectCount.setValue(prefs.get("MiniJetPro", "ObjectCount", Integer.class));
        }
        if (prefs.get("MiniJetPro", "LabelsPerObject", Integer.class) != null) {
            spLabelsPerObject.setValue(prefs.get("MiniJetPro", "LabelsPerObject", Integer.class));
        }
        if (prefs.get("MiniJetPro", "PrintLayout", Integer.class) != null) {
            spLayout.setValue(prefs.get("MiniJetPro", "PrintLayout", Integer.class));
        }
        if (prefs.get("MiniJetPro", "SaveLog", Boolean.class) != null) {
            cbSaveLogToFile.setSelected(prefs.get("MiniJetPro", "SaveLog", Boolean.class));
        }
        if (prefs.get("MiniJetPro", "WinXPos", Integer.class) != null) {
            this.setLocation(prefs.get("MiniJetPro", "WinXPos", Integer.class), prefs.get("MiniJetPro", "WinYPos", Integer.class));
        } else {
            this.setLocation(100, 60);
        }
        if (prefs.get("MiniJetPro", "WinXSize", Integer.class) != null) {
            this.setSize(prefs.get("MiniJetPro", "WinXSize", Integer.class), prefs.get("MiniJetPro", "WinYSize", Integer.class));
        } else {
            this.setSize(720, 400);
        }
    }

    private void SaveSettings() {
        File f = new File("Config.ini");
        try {
            if (f.exists() || f.createNewFile()) {
                Ini prefs = new Ini(f);
                prefs.put("MiniJetPro", "PrinterPort", cbPrinterPort.getSelectedIndex());
                prefs.put("MiniJetPro", "FolderToMonitor", tFolderToMonitor.getText());
                prefs.put("MiniJetPro", "ActiveMonitoring", cbActive.getModel().isSelected());
                prefs.put("MiniJetPro", "Command", tCommand.getText());
                prefs.put("MiniJetPro", "ObjectCount", spObjectCount.getValue());
                prefs.put("MiniJetPro", "LabelsPerObject", spLabelsPerObject.getValue());
                prefs.put("MiniJetPro", "PrintLayout", spLayout.getValue());
                prefs.put("MiniJetPro", "SaveLog", cbSaveLogToFile.getModel().isSelected());
                prefs.put("MiniJetPro", "WinXPos", this.getLocationOnScreen().x);
                prefs.put("MiniJetPro", "WinYPos", this.getLocationOnScreen().y);
                prefs.put("MiniJetPro", "WinXSize", this.getSize().width);
                prefs.put("MiniJetPro", "WinYSize", this.getSize().height);
                prefs.store(f);
            }
        } catch (IOException e) {
            textLog.append(String.format("Ошибка записи файла настроек %s\n", f.getName()));
        }
    }

    private void SaveLogToFile() {
        if (cbSaveLogToFile.getModel().isSelected() && textLog.getLineCount() > 1) {
            String logFileName = new SimpleDateFormat("yyyy.MM.dd-HH.mm.ss").format(new Date());
            try {
                FileWriter f = new FileWriter(tFolderToMonitor.getText().concat(File.separator).concat(logFileName).concat(".log"));
                f.write(textLog.getText());
                f.close();
            } catch (IOException e1) {
                textLog.append(String.format("Ошибка записи файла журнала событий %s\n", logFileName.concat(".log")));
            }
        }
    }

    public static void main(String[] args) throws IOException {
        try {
            UIManager.setLookAndFeel("com.sun.java.swing.plaf.windows.WindowsLookAndFeel");
        } catch (InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException | ClassNotFoundException ex) {
            ex.printStackTrace();
        }
        Form frame = new Form();
        frame.setContentPane(frame.panelMain);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.pack();
        frame.setVisible(true);
    }

}
