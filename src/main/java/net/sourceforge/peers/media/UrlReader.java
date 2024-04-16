/*
    This file is part of Peers, a java SIP softphone.

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
    
    Copyright 2012 Yohann Martineau 
*/
package net.sourceforge.peers.media;

import lombok.extern.slf4j.Slf4j;
import net.sourceforge.peers.Logger;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.security.AccessController;
import java.security.PrivilegedAction;

// To create an audio file for peers, you can use audacity:
//
// Edit > Preferences
//
// - Peripherals
//   - Channels: 1 (Mono)
// - Quality
//   - Sampling frequency of default: 8000 Hz
//   - Default Sample Format: 16 bits
//
// Validate
//
// Record audio
//
// File > Export
//
// - File name: test.raw
//
// Validate

@Slf4j
public class UrlReader implements SoundSource {

    public final static int BUFFER_SIZE = 256;

    private InputStream inputStream;
    private Logger logger;
    private PipedInputStream pipedInputStream;

    public static final int PIPE_SIZE = 4096;

    private PipedOutputStream pipedOutputStream = new PipedOutputStream();

    public UrlReader(String fileName, Logger logger) {
        this.logger = logger;
        try {
            URL url = new URL(fileName);

            // 打开连接
            URLConnection connection = url.openConnection();

            // 输入流读取数据
            inputStream = connection.getInputStream();
        } catch (FileNotFoundException e) {
            log.error("file not found: " + fileName, e);
            logger.error("file not found: " + fileName, e);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public synchronized void close() {
        if (inputStream != null) {
            try {
                inputStream.close();
            } catch (IOException e) {
                logger.error("io exception", e);
            }
            inputStream = null;
        }
    }


    public void init() {
        log.info("openAndStartLines");
        // AccessController.doPrivileged added for plugin compatibility
        AccessController.doPrivileged(
                new PrivilegedAction<Void>() {

                    @Override
                    public Void run() {
                        try {
                            pipedInputStream = new PipedInputStream(pipedOutputStream, PIPE_SIZE);
                        } catch (SecurityException e) {
                            log.error("security exception", e);
                            return null;
                        } catch (Throwable t) {
                            log.error("throwable " + t.getMessage());
                            return null;
                        }
                        return null;
                    }
                });

    }

    @Override
    public synchronized byte[] readData() {
        if (inputStream == null) {
            return null;
        }
        byte buffer[] = new byte[BUFFER_SIZE];
        try {
            if (inputStream.read(buffer) >= 0) {
                Thread.sleep(15);
                return buffer;
            } else {
                inputStream.close();
                inputStream = null;
            }
        } catch (IOException e) {
            log.error("io exception", e);
        } catch (InterruptedException e) {
            log.debug("file reader interrupted");
        }
        return null;
    }

}
