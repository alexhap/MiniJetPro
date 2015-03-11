# MiniJetPro
Utility to send and control print tasks on MiniJetPro M800 printer.

Tasks needs to be transcoded into ready byte-code for printer.
Tasks may be entered manually or written in text-file with csv-format (PrintCount;TaskName;CommandToPrinter).

Used libraries:
  1) Java serial port communication library - https://code.google.com/p/java-simple-serial-connector/
  2) Java .ini library - http://ini4j.sourceforge.net/
