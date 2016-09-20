/*
 * Copyright 2014-2015 the original author or authors
 *
 * Licensed under the Apache License, Version 2.0 (the “License”);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an “AS IS” BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.wplatform.ddal.message;

import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.HashMap;

import com.wplatform.ddal.engine.Constants;
import com.wplatform.ddal.jdbc.JdbcSQLException;
import com.wplatform.ddal.util.FileUtils;
import com.wplatform.ddal.util.IOUtils;
import com.wplatform.ddal.util.New;

/**
 * The trace mechanism is the logging facility of this database. There is
 * usually one trace system per database. It is called 'trace' because the term
 * 'log' is already used in the database domain and means 'transaction log'. It
 * is possible to write after close was called, but that means for each write
 * the file will be opened and closed again (which is slower).
 */
public class TraceSystem implements TraceWriter {

    /**
     * The parent trace level should be used.
     */
    public static final int PARENT = -1;

    /**
     * This trace level means nothing should be written.
     */
    public static final int OFF = 0;

    /**
     * This trace level means only errors should be written.
     */
    public static final int ERROR = 1;

    /**
     * This trace level means errors and informational messages should be
     * written.
     */
    public static final int INFO = 2;

    /**
     * This trace level means all type of messages should be written.
     */
    public static final int DEBUG = 3;

    /**
     * This trace level means all type of messages should be written, but
     * instead of using the trace file the messages should be written to SLF4J.
     */
    public static final int ADAPTER = 4;

    /**
     * The default level for system out trace messages.
     */
    public static final int DEFAULT_TRACE_LEVEL_SYSTEM_OUT = OFF;

    /**
     * The default level for file trace messages.
     */
    public static final int DEFAULT_TRACE_LEVEL_FILE = ERROR;

    /**
     * The default maximum trace file size. It is currently 64 MB. Additionally,
     * there could be a .old file of the same size.
     */
    private static final int DEFAULT_MAX_FILE_SIZE = 64 * 1024 * 1024;

    private static final int CHECK_SIZE_EACH_WRITES = 4096;

    private int levelSystemOut = DEFAULT_TRACE_LEVEL_SYSTEM_OUT;
    private int levelFile = DEFAULT_TRACE_LEVEL_FILE;
    private int levelMax;
    private int maxFileSize = DEFAULT_MAX_FILE_SIZE;
    private String fileName;
    private HashMap<String, Trace> traces;
    private SimpleDateFormat dateFormat;
    private Writer fileWriter;
    private PrintWriter printWriter;
    private int checkSize;
    private boolean closed;
    private boolean writingErrorLogged;
    private TraceWriter writer = this;
    private PrintStream sysOut = System.out;

    /**
     * Create a new trace system object.
     *
     * @param fileName the file name
     */
    public TraceSystem(String fileName) {
        this.fileName = fileName;
        updateLevel();
    }

    private void updateLevel() {
        levelMax = Math.max(levelSystemOut, levelFile);
    }

    /**
     * Set the print stream to use instead of System.out.
     *
     * @param out the new print stream
     */
    public void setSysOut(PrintStream out) {
        this.sysOut = out;
    }

    /**
     * Get or create a trace object for this module. Trace modules with names
     * such as "JDBC[1]" are not cached (modules where the name ends with "]").
     * All others are cached.
     *
     * @param module the module name
     * @return the trace object
     */
    public synchronized Trace getTrace(String module) {
        if (module.endsWith("]")) {
            return new Trace(writer, module);
        }
        if (traces == null) {
            traces = New.hashMap(16);
        }
        Trace t = traces.get(module);
        if (t == null) {
            t = new Trace(writer, module);
            traces.put(module, t);
        }
        return t;
    }

    @Override
    public boolean isEnabled(int level) {
        return level <= this.levelMax;
    }

    /**
     * Set the trace file name.
     *
     * @param name the file name
     */
    public void setFileName(String name) {
        this.fileName = name;
    }

    /**
     * Set the maximum trace file size in bytes.
     *
     * @param max the maximum size
     */
    public void setMaxFileSize(int max) {
        this.maxFileSize = max;
    }

    /**
     * Set the trace level to use for System.out
     *
     * @param level the new level
     */
    public void setLevelSystemOut(int level) {
        levelSystemOut = level;
        updateLevel();
    }

    public int getLevelFile() {
        return levelFile;
    }

    /**
     * Set the file trace level.
     *
     * @param level the new level
     */
    public void setLevelFile(int level) {
        if (level == ADAPTER) {
            /*
            String adapterClass = "com.wplatform.ddal.message.TraceWriterAdapter";
            try {
                writer = (TraceWriter) Class.forName(adapterClass).newInstance();
            } catch (Throwable e) {
                e = DbException.get(ErrorCode.CLASS_NOT_FOUND_1, e, adapterClass);
                write(ERROR, Trace.DATABASE, adapterClass, e);
                return;
            }*/
            writer = new TraceWriterAdapter();
            String name = fileName;
            if (name != null) {
                if (name.endsWith(Constants.SUFFIX_TRACE_FILE)) {
                    name = name.substring(0, name.length() - Constants.SUFFIX_TRACE_FILE.length());
                }
                int idx = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
                if (idx >= 0) {
                    name = name.substring(idx + 1);
                }
                writer.setName(name);
            }
        }
        levelFile = level;
        updateLevel();
    }

    private synchronized String format(String module, String s) {
        if (dateFormat == null) {
            dateFormat = new SimpleDateFormat("MM-dd HH:mm:ss ");
        }
        return dateFormat.format(System.currentTimeMillis()) + module + ": " + s;
    }

    @Override
    public void write(int level, String module, String s, Throwable t) {
        if (level <= levelSystemOut || level > this.levelMax) {
            // level <= levelSystemOut: the system out level is set higher
            // level > this.level: the level for this module is set higher
            sysOut.println(format(module, s));
            if (t != null && levelSystemOut == DEBUG) {
                t.printStackTrace(sysOut);
            }
        }
        if (fileName != null) {
            if (level <= levelFile) {
                writeFile(format(module, s), t);
            }
        }
    }

    private synchronized void writeFile(String s, Throwable t) {
        try {
            if (checkSize++ >= CHECK_SIZE_EACH_WRITES) {
                checkSize = 0;
                closeWriter();
                if (maxFileSize > 0 && FileUtils.size(fileName) > maxFileSize) {
                    String old = fileName + ".old";
                    FileUtils.delete(old);
                    FileUtils.move(fileName, old);
                }
            }
            if (!openWriter()) {
                return;
            }
            printWriter.println(s);
            if (t != null) {
                if (levelFile == ERROR && t instanceof JdbcSQLException) {
                    JdbcSQLException se = (JdbcSQLException) t;
                    int code = se.getErrorCode();
                    if (ErrorCode.isCommon(code)) {
                        printWriter.println(t.toString());
                    } else {
                        t.printStackTrace(printWriter);
                    }
                } else {
                    t.printStackTrace(printWriter);
                }
            }
            printWriter.flush();
            if (closed) {
                closeWriter();
            }
        } catch (Exception e) {
            logWritingError(e);
        }
    }

    private void logWritingError(Exception e) {
        if (writingErrorLogged) {
            return;
        }
        writingErrorLogged = true;
        Exception se = DbException.get(
                ErrorCode.TRACE_FILE_ERROR_2, e, fileName, e.toString());
        // print this error only once
        fileName = null;
        sysOut.println(se);
        se.printStackTrace();
    }

    private boolean openWriter() {
        if (printWriter == null) {
            try {
                FileUtils.createDirectories(FileUtils.getParent(fileName));
                if (FileUtils.exists(fileName) && !FileUtils.canWrite(fileName)) {
                    // read only database: don't log error if the trace file
                    // can't be opened
                    return false;
                }
                fileWriter = IOUtils.getBufferedWriter(
                        FileUtils.newOutputStream(fileName, true));
                printWriter = new PrintWriter(fileWriter, true);
            } catch (Exception e) {
                logWritingError(e);
                return false;
            }
        }
        return true;
    }

    private synchronized void closeWriter() {
        if (printWriter != null) {
            printWriter.flush();
            printWriter.close();
            printWriter = null;
        }
        if (fileWriter != null) {
            try {
                fileWriter.close();
            } catch (IOException e) {
                // ignore
            }
            fileWriter = null;
        }
    }

    /**
     * Close the writers, and the files if required. It is still possible to
     * write after closing, however after each write the file is closed again
     * (slowing down tracing).
     */
    public void close() {
        closeWriter();
        closed = true;
    }

    @Override
    public void setName(String name) {
        // nothing to do (the file name is already set)
    }

}
