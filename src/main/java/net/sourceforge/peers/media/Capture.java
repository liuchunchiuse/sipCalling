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
    
    Copyright 2008, 2009, 2010, 2011 Yohann Martineau 
*/

package net.sourceforge.peers.media;

import com.net.sipcall.sipcalling.utils.ByteUtils;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.peers.Logger;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

@Slf4j
public class Capture implements Runnable {

    public static final int SAMPLE_SIZE = 16;
    public static final int BUFFER_SIZE = SAMPLE_SIZE * 20;

    private PipedOutputStream rawData;
    private boolean isStopped;
    private SoundSource soundSource;
    private Logger logger;
    private CountDownLatch latch;

    public Capture(PipedOutputStream rawData, SoundSource soundSource,
                   Logger logger, CountDownLatch latch) {
        this.rawData = rawData;
        this.soundSource = soundSource;
        this.logger = logger;
        this.latch = latch;
        isStopped = false;
    }

    @Override
    public void run() {
        byte[] buffer;
//        String filePath = "F:/lcc/workSpace/digital-audio-filter/data/有声.wav";
        String filePath = "F:/lcc/workSpace/sipCalling/lcc.wav";
        List<byte[]> finalResult = new ArrayList<>();

        /*try {
            //普通文件流
            FileInputStream fileInputStream = new FileInputStream(filePath);
            BufferedInputStream bufferedInputStream = new BufferedInputStream(fileInputStream);



            //获取音频文件流
            File audioFile = new File(filePath);
            AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(audioFile);
            audioInputStream.getFormat().getSampleRate();

      *//*      // 读取文件头信息
            byte[] header = new byte[44];
            audioInputStream.read(header);
            // 创建一个缓冲区用于存储音频数据
            buffer = new byte[1024];*//*

            // 获取音频文件的信息
            long fileSize = audioFile.length();
            log.info("音频文件长度:{}", fileSize);

            byte[] audioBytes = new byte[(int) fileSize];
            // 读取音频文件到byte数组
            int bytesRead = audioInputStream.read(audioBytes);


            // 读取音频数据
//            int bytesRead;
            while (!isStopped) {
               *//* while ((bytesRead = audioInputStream.read(buffer)) != -1) {
                    log.info("读取的字节数组长度:{}", buffer.length);
                    log.info("读取的字节数组:{}", buffer);
                    try {
                        rawData.write(buffer);
                        rawData.flush();
                    } catch (IOException e) {
                        logger.error("input/output error", e);
                        return;
                    }
                }*//*

                rawData.write(audioBytes);
                rawData.flush();
            }


        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (UnsupportedAudioFileException e) {
            throw new RuntimeException(e);
        }*/
        while (!isStopped) {
            buffer = soundSource.readData();
            log.info("麦克风的字节数组长度:{}", buffer.length);
            log.info("麦克风的字节:{}", buffer);
            try {
                if (buffer == null) {
                    break;
                }
                rawData.write(buffer);
                rawData.flush();
            } catch (IOException e) {
                logger.error("input/output error", e);
                return;
            }
        }
        latch.countDown();
        if (latch.getCount() != 0) {
            try {
                latch.await();
            } catch (InterruptedException e) {
                logger.error("interrupt exception", e);
            }
        }
    }

    public synchronized void setStopped(boolean isStopped) {
        this.isStopped = isStopped;
    }

}
