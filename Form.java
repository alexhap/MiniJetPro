/**
 * Created by alexhap on 18.02.2015.
 *
 */

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import javax.swing.*;
import javax.swing.Timer;
import javax.swing.table.*;
import javax.swing.text.DefaultCaret;
import org.ini4j.Ini;
import sun.swing.table.DefaultTableCellHeaderRenderer;

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
    private JButton bPause;
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
    private final Timer timerAutoSuspend;
    private JPopupMenu menu;

    private boolean RowSendComplete;
    private boolean TableSendComplete;
    private int CurrentRow;
    private ComCore cc = null;
    private int layerSelectDelay;
    private boolean paused;

    @Override
    public void windowOpened(WindowEvent e) {
        LoadSettings();
        cc = new ComCore(layerSelectDelay);
        cc.addObserver(this);
    }

    @Override
    public void windowClosing(WindowEvent e) {
        stopCore();
        if (cc != null) cc.deleteObservers();
        cc = null;
        SaveSettings();
        SaveLogToFile();
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

    public Form() {
        this.addWindowListener(this);

        bFolderChoose.addActionListener(e -> onFolderChooseAction());
        bLoadTasks.addActionListener(e -> onLoadTaskAction());
        bSendTest.addActionListener(e -> onSendTestAction());
        bSendActiveString.addActionListener(e -> onSendActiveRowAction());
        bSendAll.addActionListener(e -> onSendAllAction());
        bStop.addActionListener(e -> onStopAction());
        bClearLog.addActionListener(e -> onClearLogAction());
        bPause.addActionListener(e -> onPauseAction());
        spObjectCount.addChangeListener(e -> onObjectCountChange());
        spLabelsPerObject.addChangeListener(e -> onLabelsPerObjectChange());
        spLayout.addChangeListener(e -> onLayoutChange());
        cbPrinterPort.addActionListener(e -> onPrinterPortChange());
        cbActive.addActionListener(e -> onCheckActiveAction());
        timerSend = new Timer(100, e -> timerSendAction()); // from row to next row latency
        timerMonitor = new Timer(500, e -> timerMonitorAction()); // timer monitoring for a new file
        timerStatus = new Timer(333, e -> timerStatusAction()); // for status show
        timerAutoSuspend = new Timer(5000, e -> timerSuspendAction()); // no work -> disconnect (printer is less buggy with it)

        menu = new JPopupMenu();
        JMenuItem menuItem = new JMenuItem("Сдвинуть вверх"); //UIManager.getIcon("Table.ascendingSortIcon")
        menuItem.addActionListener(e -> {
            int row = tTasks.getSelectedRow();
            if (row > 0) {
                DefaultTableModel tm = (DefaultTableModel) tTasks.getModel();
                tm.getDataVector().add(row - 1, tm.getDataVector().get(row));
                tm.getDataVector().remove(row + 1);
                tm.fireTableDataChanged();
                tTasks.setRowSelectionInterval(row - 1, row - 1);
            }
        });
        menu.add(menuItem);
        menuItem = new JMenuItem("Сдвинуть вниз"); // UIManager.getIcon("Table.descendingSortIcon")
        menuItem.addActionListener(e -> {
            int row = tTasks.getSelectedRow();
            if (row > -1 && row < tTasks.getRowCount() - 1) {
                DefaultTableModel tm = (DefaultTableModel) tTasks.getModel();
                tm.getDataVector().add(row, tm.getDataVector().get(row + 1));
                tm.getDataVector().remove(row + 2);
                tm.fireTableDataChanged();
                tTasks.setRowSelectionInterval(row + 1, row + 1);
            }
        });
        menu.add(menuItem);
        menuItem = new JMenuItem("Удалить задание"); // UIManager.getIcon("Table.???")
        menuItem.addActionListener(e -> {
            int row = tTasks.getSelectedRow();
            if (row > -1 && row < tTasks.getRowCount()) {
                DefaultTableModel tm = (DefaultTableModel) tTasks.getModel();
                tm.getDataVector().remove(row);
                tm.fireTableDataChanged();
            }
        });
        menu.add(menuItem);
        tTasks.setComponentPopupMenu(menu);

        tTasks.addMouseListener(new MouseAdapter() {
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

        layerSelectDelay = 1200; // time for executing B_SELECT_MESSAGE (layer) BEFORE your command
        RowSendComplete = true;
        TableSendComplete = true;
        CurrentRow = 0;
        paused = false;
        setMinimumSize(new Dimension(720, 400));
        setTitle("MiniJetPro printer control");
    }

    private void onPauseAction() {
        paused = !paused;
        bPause.setText(paused ? "Продолжить" : "Пауза");
        bPause.getModel().setPressed(paused);
        if (cc != null) cc.setPause(paused);
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
        clearStatus();
        SendCommand((int) spLayout.getValue(), "Тестовая строка", (int) spObjectCount.getValue(),
                (int) spLabelsPerObject.getValue(), tCommand.getText());
    }

    private void onLoadTaskAction() {
        LoadTasks("", true);
    }

    private void onSendActiveRowAction() {
        clearStatus();
        if (tTasks.getRowCount() > 0 && tTasks.getSelectedRowCount() > 0) {
            SendTableRow(tTasks.getSelectedRow());
        }
    }

    private void onSendAllAction() {
        clearStatus();
        SendTable();
    }

    private void onStopAction() {
        if (paused) {
            onPauseAction(); // pause off, because it is the same command Stop for printer
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
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
        suspendCore();
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
        }
    }

    private void timerSuspendAction() {
        suspendCore();
        timerAutoSuspend.stop();
    }

    private void clearStatus() {
        lStatus1.setText("-");
        lStatus2.setText("-");
        lStatus3.setText("-");
        lStatus4.setText("-");
    }

    private void suspendCore() {
        if (cc != null) cc.suspendCore();
    }

    private void stopCore() {
        if (cc != null) cc.stopCore();
    }

    private void SendCommand(int layout, String artikul, int objCount, int labelCount, String command) {
        RowSendComplete = false;
        timerAutoSuspend.stop();
        cc.setDebugLog(cbDebugLog.getModel().isSelected());
        if (cc != null && cc.startCore(cbPrinterPort.getSelectedItem().toString())) {
            cc.sendData(layout, artikul, objCount, labelCount, command);
        } else {
            textLog.append(String.format("Запуск модуля отправки для %s не удался.\n", cbPrinterPort.getSelectedItem().toString()));
        }
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
                        textLog.append(String.format("Ошибка данных в строке %d:'%s'\n", index, str));
                    }
                }
                br.close();
                TableColumnModel tcm = tTasks.getColumnModel();
                int[] tw = {60, 150, 60, 60, 250};
                for (int i = 0; i < 5; i++) {
                    tcm.getColumn(i).setWidth(tw[i]);
                    tcm.getColumn(i).setMinWidth(tw[i]);
                    tcm.getColumn(i).setMaxWidth(tw[i]*4);
                }
                tcm.getColumn(2).setCellEditor(new DefaultCellEditor(new JTextField()));
                tTasks.setDefaultEditor(tTasks.getColumnClass(0), null);
                tTasks.getTableHeader().setDefaultRenderer(new DefaultTableCellHeaderRenderer());
                tTasks.setGridColor(Color.GRAY);
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

    public void update(Observable pr, Object arg) { // arg need to be upgraded to new structured class.
        String str = (String) arg;
        textLog.append(str);
        if (str.endsWith("(ON)\n")) {
            if (!timerStatus.isRunning()) {
                SetInterface(false);
                timerStatus.start();
            }
        } else if (str.endsWith("(OFF)\n")) {
            SetInterface(true);
            timerStatus.stop();
            timerAutoSuspend.start();
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
        spLabelsPerObject.setEnabled(mode);
        spLayout.setEnabled(mode);
        bLoadTasks.setEnabled(mode);
        bSendActiveString.setEnabled(mode);
        bSendAll.setEnabled(mode);
        bSendTest.setEnabled(mode);
    }

    private void LoadSettings() {
        Ini settings;
        String iniGroup = "MiniJetPro";
        try {
            settings = new Ini(new File("Config.ini"));
        } catch (IOException e) {
            settings = new Ini();
            settings.put(iniGroup, "PrinterPort", 0);
            settings.put(iniGroup, "FolderToMonitor", "");
            settings.put(iniGroup, "ActiveMonitoring", false);
            settings.put(iniGroup, "Command", "162A010250530D");
            settings.put(iniGroup, "ObjectCount", 1);
            settings.put(iniGroup, "LabelsPerObject", 1);
            settings.put(iniGroup, "PrintLayout", 1);
            settings.put(iniGroup, "SaveLog", false);
            settings.put(iniGroup, "WinXPos", 1);
            settings.put(iniGroup, "WinYPos", 1);
            settings.put(iniGroup, "LayerSelectDelay", 1200);
            settings.put(iniGroup, "WinXSize", 720);
            settings.put(iniGroup, "WinYSize", 400);
        }
        if (settings.get(iniGroup, "PrinterPort", Integer.class) != null) {
            cbPrinterPort.setSelectedIndex(settings.get(iniGroup, "PrinterPort", Integer.class));
        }
        String folder = settings.get(iniGroup, "FolderToMonitor", String.class);
        if (!folder.equals("")) {
            File f = new File(folder);
            if (f.exists()) {
                tFolderToMonitor.setText(folder);
                if (settings.get(iniGroup, "ActiveMonitoring", Boolean.class) != null) {
                    cbActive.setSelected(settings.get(iniGroup, "ActiveMonitoring", Boolean.class));
                    if (cbActive.getModel().isSelected()) {
                        timerMonitor.start();
                    }
                }
            }
        }
        if (settings.get(iniGroup, "Command", String.class) != null) {
            tCommand.setText(settings.get(iniGroup, "Command", String.class));
        }
        if (settings.get(iniGroup, "ObjectCount", Integer.class) != null) {
            spObjectCount.setValue(settings.get(iniGroup, "ObjectCount", Integer.class));
        }
        if (settings.get(iniGroup, "LabelsPerObject", Integer.class) != null) {
            spLabelsPerObject.setValue(settings.get(iniGroup, "LabelsPerObject", Integer.class));
        }
        if (settings.get(iniGroup, "PrintLayout", Integer.class) != null) {
            spLayout.setValue(settings.get(iniGroup, "PrintLayout", Integer.class));
        }
        if (settings.get(iniGroup, "SaveLog", Boolean.class) != null) {
            cbSaveLogToFile.setSelected(settings.get(iniGroup, "SaveLog", Boolean.class));
        }
        if (settings.get(iniGroup, "LayerSelectDelay", Integer.class) != null) {
            layerSelectDelay = settings.get(iniGroup, "LayerSelectDelay", Integer.class);
        }
        if (settings.get(iniGroup, "WinXPos", Integer.class) != null) {
            this.setLocation(settings.get(iniGroup, "WinXPos", Integer.class), settings.get(iniGroup, "WinYPos", Integer.class));
        } else {
            this.setLocation(100, 60);
        }
        if (settings.get(iniGroup, "WinXSize", Integer.class) != null) {
            this.setSize(settings.get(iniGroup, "WinXSize", Integer.class), settings.get(iniGroup, "WinYSize", Integer.class));
        } else {
            this.setSize(720, 400);
        }
    }

    private void SaveSettings() {
        File iniFile = new File("Config.ini");
        String iniGroup = "MiniJetPro";
        try {
            if (iniFile.exists() || iniFile.createNewFile()) {
                Ini settings = new Ini(iniFile);
                settings.put(iniGroup, "PrinterPort", cbPrinterPort.getSelectedIndex());
                settings.put(iniGroup, "FolderToMonitor", tFolderToMonitor.getText());
                settings.put(iniGroup, "ActiveMonitoring", cbActive.getModel().isSelected());
                settings.put(iniGroup, "Command", tCommand.getText());
                settings.put(iniGroup, "ObjectCount", spObjectCount.getValue());
                settings.put(iniGroup, "LabelsPerObject", spLabelsPerObject.getValue());
                settings.put(iniGroup, "PrintLayout", spLayout.getValue());
                settings.put(iniGroup, "SaveLog", cbSaveLogToFile.getModel().isSelected());
                settings.put(iniGroup, "LayerSelectDelay", layerSelectDelay);
                settings.put(iniGroup, "WinXPos", this.getLocationOnScreen().x);
                settings.put(iniGroup, "WinYPos", this.getLocationOnScreen().y);
                settings.put(iniGroup, "WinXSize", this.getSize().width);
                settings.put(iniGroup, "WinYSize", this.getSize().height);
                settings.store(iniFile);
            }
        } catch (IOException e) {
            textLog.append(String.format("Ошибка записи файла настроек %s\n", iniFile.getName()));
        }
    }

    private void SaveLogToFile() {
        if (cbSaveLogToFile.getModel().isSelected() && textLog.getLineCount() > 1) {
            String logFileName = new SimpleDateFormat("yyyy.MM.dd-HH.mm.ss").format(new Date()).concat(".log");
            try {
                FileWriter f = new FileWriter(tFolderToMonitor.getText().concat(File.separator).concat(logFileName));
                f.write(textLog.getText());
                f.close();
            } catch (IOException e1) {
                textLog.append(String.format("Ошибка записи файла журнала событий %s\n", logFileName));
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
