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
    
    Copyright 2007-2013 Yohann Martineau 
*/

package net.sourceforge.peers.media;

import lombok.extern.slf4j.Slf4j;
import net.sourceforge.peers.Logger;
import net.sourceforge.peers.rtp.RFC3551;
import net.sourceforge.peers.rtp.RtpSession;
import net.sourceforge.peers.sdp.Codec;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.CountDownLatch;

@Slf4j
public class CaptureRtpSender {

    public static final int PIPE_SIZE = 4096;

    private RtpSession rtpSession;
    private Capture capture;
    private Encoder encoder;
    private RtpSender rtpSender;

    public CaptureRtpSender(RtpSession rtpSession, SoundSource soundSource,
                            boolean mediaDebug, Codec codec, Logger logger, String peersHome)
            throws IOException {
        super();
        this.rtpSession = rtpSession;
        // the use of PipedInputStream and PipedOutputStream in Capture,
        // Encoder and RtpSender imposes a synchronization point at the
        // end of life of those threads to a void read end dead exceptions
        CountDownLatch latch = new CountDownLatch(3);
        //未编码的输出流
        PipedOutputStream rawDataOutput = new PipedOutputStream();
        //未编码的输入流
        PipedInputStream rawDataInput;
        try {
            //与未编码的输出流建立连接,input负责输入,out负责输出
            rawDataInput = new PipedInputStream(rawDataOutput, PIPE_SIZE);
        } catch (IOException e) {
            log.error("input/output error", e);
            return;
        }

        //编码的输出流
        PipedOutputStream encodedDataOutput = new PipedOutputStream();
        //编码的输入流
        PipedInputStream encodedDataInput;
        try {
            //与编码的输出流建立连接,input负责输入,out负责输出
            encodedDataInput = new PipedInputStream(encodedDataOutput,
                    PIPE_SIZE);
        } catch (IOException e) {
            log.error("input/output error");
            rawDataInput.close();
            return;
        }
        //读取音频数据到out
        capture = new Capture(rawDataOutput, soundSource, logger, latch);
        switch (codec.getPayloadType()) {
            case RFC3551.PAYLOAD_TYPE_PCMU:
                encoder = new PcmuEncoder(rawDataInput, encodedDataOutput,
                        mediaDebug, logger, peersHome, latch);
                break;
            case RFC3551.PAYLOAD_TYPE_PCMA:
                encoder = new PcmaEncoder(rawDataInput, encodedDataOutput,
                        mediaDebug, logger, peersHome, latch);
                break;
            default:
                encodedDataInput.close();
                rawDataInput.close();
                throw new RuntimeException("unknown payload type");
        }
        rtpSender = new RtpSender(encodedDataInput, rtpSession, mediaDebug,
                codec, logger, peersHome, latch);
    }

    public void start() throws IOException {

        capture.setStopped(false);
        encoder.setStopped(false);
        rtpSender.setStopped(false);

        Thread captureThread = new Thread(capture,
                Capture.class.getSimpleName());
        Thread encoderThread = new Thread(encoder,
                Encoder.class.getSimpleName());
        Thread rtpSenderThread = new Thread(rtpSender,
                RtpSender.class.getSimpleName());

        captureThread.start();
        encoderThread.start();
        rtpSenderThread.start();

    }

    public void stop() {
        if (capture != null) {
            capture.setStopped(true);
        }
        if (encoder != null) {
            encoder.setStopped(true);
        }
        if (rtpSender != null) {
            rtpSender.setStopped(true);
        }
    }

    public synchronized RtpSession getRtpSession() {
        return rtpSession;
    }

    public RtpSender getRtpSender() {
        return rtpSender;
    }

}
