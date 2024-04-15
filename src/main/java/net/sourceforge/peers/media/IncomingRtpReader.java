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
    
    Copyright 2007, 2008, 2009, 2010 Yohann Martineau 
*/

package net.sourceforge.peers.media;

import com.net.sipcall.sipcalling.utils.ByteUtils;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.peers.Logger;
import net.sourceforge.peers.rtp.RFC3551;
import net.sourceforge.peers.rtp.RtpListener;
import net.sourceforge.peers.rtp.RtpPacket;
import net.sourceforge.peers.rtp.RtpSession;
import net.sourceforge.peers.sdp.Codec;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.*;
import java.lang.reflect.Array;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 接收进来的实时信息读取
 */
@Slf4j
public class IncomingRtpReader implements RtpListener {

    private RtpSession rtpSession;
    private AbstractSoundManager soundManager;
    private Decoder decoder;

    public IncomingRtpReader(RtpSession rtpSession,
                             AbstractSoundManager soundManager, Codec codec, Logger logger)
            throws IOException {
        logger.debug("playback codec:" + codec.toString().trim());
        this.rtpSession = rtpSession;
        this.soundManager = soundManager;
//        switch (codec.getPayloadType()) {
        switch (codec.getPayloadType()) {
            case RFC3551.PAYLOAD_TYPE_PCMU:
                decoder = new PcmuDecoder();
                break;
            case RFC3551.PAYLOAD_TYPE_PCMA:
                decoder = new PcmaDecoder();
                break;
            default:
                throw new RuntimeException("unsupported payload type");
        }
        rtpSession.addRtpListener(this);
    }

    public void start() {
        rtpSession.start();
    }


    AudioFormat format = new AudioFormat(8000, 16, 1, true, false);


    // 写入文件
    File outputFile = new File("lcc.wav");

    private List<byte[]> finalResult = new ArrayList<>();

    /**
     * 接收的声音处理
     * @param rtpPacket
     */
    @Override
    public void receivedRtpPacket(RtpPacket rtpPacket) {
        byte[] rawBuf = decoder.process(rtpPacket.getData());
        if (rawBuf.length > 0) {
            finalResult.add(rawBuf);
            log.info("字节长度:{}", rawBuf.length);
            log.info("字节总数量:{}", finalResult.size());
            //生成一个wav音频文件
            if (finalResult.size() == 1000) {
                try {
                    byte[] result = ByteUtils.mergeBytes(finalResult);
                    ByteArrayInputStream bais = new ByteArrayInputStream(result);
                    AudioInputStream audioInputStream = new AudioInputStream(bais, format, result.length / format.getFrameSize());
                    AudioSystem.write(audioInputStream, AudioFileFormat.Type.WAVE, outputFile);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        if (soundManager != null) {
            soundManager.writeData(rawBuf, 0, rawBuf.length);
        }
    }

}
