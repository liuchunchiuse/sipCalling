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

import net.sourceforge.peers.Logger;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * 通过大模型回复获取文件流
 */
public class ModelReader implements SoundSource {

    public final static int BUFFER_SIZE = 256;

    private FileInputStream fileInputStream;
    private Logger logger;

    public ModelReader(String fileName, Logger logger) {
        this.logger = logger;
        try {
            fileInputStream = new FileInputStream(fileName);
        } catch (FileNotFoundException e) {
            logger.error("file not found: " + fileName, e);
        }
    }

    public synchronized void close() {
        if (fileInputStream != null) {
            try {
                fileInputStream.close();
            } catch (IOException e) {
                logger.error("io exception", e);
            }
            fileInputStream = null;
        }
    }

    @Override
    public synchronized byte[] readData() {
        if (fileInputStream == null) {
            return null;
        }
        byte buffer[] = new byte[BUFFER_SIZE];
        try {
            if (fileInputStream.read(buffer) >= 0) {
                Thread.sleep(15);
                return buffer;
            } else {
                fileInputStream.close();
                fileInputStream = null;
            }
        } catch (IOException e) {
            logger.error("io exception", e);
        } catch (InterruptedException e) {
            logger.debug("file reader interrupted");
        }
        return null;
    }

}
