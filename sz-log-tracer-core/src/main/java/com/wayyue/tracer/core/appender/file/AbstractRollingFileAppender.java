package com.wayyue.tracer.core.appender.file;


import com.wayyue.tracer.core.appender.TraceAppender;
import com.wayyue.tracer.core.appender.TracerLogRootDaemon;
import com.wayyue.tracer.core.appender.sefllog.SelfDefineLog;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author jinming.xiao
 * @since 2020/06/01
 */
public abstract class AbstractRollingFileAppender implements TraceAppender {

    /**
     * The log refresh interval, when the buffer time exceeds the interval, the cached log data is refreshed.
     */
    private static final long LOG_FLUSH_INTERVAL = TimeUnit.SECONDS.toMillis(1);
    /**
     * Default output buffer size 8KB
     */
    public static final int DEFAULT_BUFFER_SIZE = 8 * 1024;
    private static final long IOEXCEPTION_PRINT_INTERVAL = 60 * 1000;

    /**
     * Log cache buffer size
     */
    private final int bufferSize;

    protected final String fileName;

    private final AtomicBoolean isRolling = new AtomicBoolean(false);

    protected File logFile = null;

    protected BufferedOutputStream bos = null;

    private long nextFlushTime = 0L;
    private long nextIOExceptionPrintTime = 0L;

    public AbstractRollingFileAppender(String file, boolean append) {
        this(file, DEFAULT_BUFFER_SIZE, append);
    }

    public AbstractRollingFileAppender(String file, int bufferSize, boolean append) {
        this.fileName = TracerLogRootDaemon.LOG_FILE_DIR + File.separator + file;
        this.bufferSize = bufferSize;
        setFile(append);
    }

    protected void setFile(boolean append) {
        try {
            logFile = new File(fileName);
            if (!logFile.exists()) {
                File parentFile = logFile.getParentFile();
                if (!parentFile.exists() && !parentFile.mkdirs()) {
                    doSelfLog("[ERROR] Fail to mkdirs: " + parentFile.getAbsolutePath());
                    return;
                }
                if (!logFile.createNewFile()) {
                    doSelfLog("[ERROR] Fail to create file to write: " + logFile.getAbsolutePath());
                    return;
                }
            }
            if (!logFile.isFile() || !logFile.canWrite()) {
                doSelfLog("[ERROR] Invalid file, exists=" + logFile.exists() + ", isFile="
                        + logFile.isFile() + ", canWrite=" + logFile.canWrite() + ", path="
                        + logFile.getAbsolutePath());
                return;
            }
            //append == true
            FileOutputStream ostream = new FileOutputStream(logFile, append);
            bos = new BufferedOutputStream(ostream, bufferSize);
        } catch (Throwable e) {
            SelfDefineLog.error("setFile error", e);
        }
    }

    @Override
    public void append(String log) throws IOException {
        if (bos != null) {
            waitUntilRollFinish();
            if (shouldRollOverNow() && isRolling.compareAndSet(false, true)) {
                try {
                    rollOver();
                    nextFlushTime = System.currentTimeMillis() + LOG_FLUSH_INTERVAL;
                } finally {
                    isRolling.set(false);
                }
            } else {
                // Refreshed after the specified refresh time has not been refreshed
                long now;
                if ((now = System.currentTimeMillis()) >= nextFlushTime) {
                    flush();
                    nextFlushTime = now + LOG_FLUSH_INTERVAL;
                }
            }
            // Whether you have RollOver or not, you need to write the input to bos
            byte[] bytes = log.getBytes(TracerLogRootDaemon.DEFAULT_CHARSET);
            write(bytes);
        }
    }

    /**
     * Whether to scroll right now
     *
     * @return true
     */
    protected abstract boolean shouldRollOverNow();

    /**
     * Ready to RollOver
     * <p>
     * WARNING：Do not use SelfLog when logging RollOver,
     * because this time it is possible that SelfLog is in RollOver itself.
     */
    protected abstract void rollOver();

    private void write(byte[] bytes) {
        try {
            bos.write(bytes);
        } catch (IOException e) {
            long now = System.currentTimeMillis();
            if (now > nextIOExceptionPrintTime) {
                nextIOExceptionPrintTime = now + IOEXCEPTION_PRINT_INTERVAL;
                SelfDefineLog.error("Failed to write file " + fileName, e);
            }
        }
    }

    /**
     * Refreshed after the specified refresh time has not been refreshed
     */
    @Override
    public void flush() {
        if (bos != null) {
            try {
                bos.flush();
            } catch (IOException e) {
                long now = System.currentTimeMillis();
                if (now > nextIOExceptionPrintTime) {
                    nextIOExceptionPrintTime = now + IOEXCEPTION_PRINT_INTERVAL;
                    SelfDefineLog.error("Failed to flush file " + fileName, e);
                }
            }
        }
    }

    void waitUntilRollFinish() {
        while (isRolling.get()) {
            try {
                Thread.sleep(1L);
            } catch (Exception e) {
                SelfDefineLog.error("WaitUntilRollFinish error!", e);
            }
        }
    }

    private void doSelfLog(String log) {
        System.out.println("[TraceSelfLog]" + log);
    }
}
