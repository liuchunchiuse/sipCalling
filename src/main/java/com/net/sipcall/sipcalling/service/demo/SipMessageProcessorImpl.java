package com.net.sipcall.sipcalling.service.demo;

import cn.hutool.json.JSONUtil;
import com.net.sipcall.sipcalling.service.demo.util.MD5Util;
import com.net.sipcall.sipcalling.utils.SipUtils;
import gov.nist.javax.sip.message.SIPRequest;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.peers.Logger;
import net.sourceforge.peers.javaxsound.JavaxSoundManager;
import net.sourceforge.peers.media.*;
import net.sourceforge.peers.rtp.RtpSession;
import net.sourceforge.peers.sdp.Codec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.sdp.*;
import javax.sip.*;
import javax.sip.address.Address;
import javax.sip.address.AddressFactory;
import javax.sip.address.SipURI;
import javax.sip.address.URI;
import javax.sip.header.*;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.text.ParseException;
import java.util.*;

/**
 * @Author: cth
 * @Date: 2019/5/16 17:47
 * @Description: 国标协议处理实现业务类
 */
@Slf4j
@Service
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SipMessageProcessorImpl implements SipMessageProcessor {

    @Value("${gb28181.password:123456}")
    String password;
    String MST_TYPE_KEEPALIVE = "<CmdType>Keepalive</CmdType>";
    String MST_TYPE_CATALOG = "<CmdType>Catalog</CmdType>";
    String XML_HEAD = "<?xml version=";

    private RtpSession rtpSession;

    @Getter
    @Setter
    private DatagramSocket datagramSocket;

    private Logger logger;

    private CaptureRtpSender captureRtpSender;

    private int remotePort;

    @Override
    public void processRequest(RequestEvent requestEvent, AddressFactory addressFactory, MessageFactory messageFactory, HeaderFactory headerFactory, SipProvider sipProvider) {
        Request request = requestEvent.getRequest();
        if (null == request) {
            log.error("processRequest RequestEvent is null");
            return;
        }
        switch (request.getMethod().toUpperCase()) {
            case Request.MESSAGE:
                log.debug("收到MESSAGE的请求");
                doRequestMessage(requestEvent, addressFactory, messageFactory, headerFactory, sipProvider);
                break;
            case Request.REGISTER:
                log.info("收到下级域REGISTER的请求");
                doRequestRegister(requestEvent, addressFactory, messageFactory, headerFactory, sipProvider);
                break;
            case Request.ACK:
                doRequestAsk(requestEvent, addressFactory, messageFactory, headerFactory, sipProvider);
                break;
            case Request.BYE:
                log.info("收到BYE的请求");
                doRequestBye(requestEvent, addressFactory, messageFactory, headerFactory, sipProvider);
                break;
            case Request.INVITE:
                log.info("Incoming call...");
                doRequestInvite(requestEvent, addressFactory, messageFactory, headerFactory, sipProvider);
                break;
            default:
                log.info("不处理的requestMethod：" + request.getMethod().toUpperCase());
        }
    }

    @Override
    public void processRequest(RequestEvent requestEvent, AddressFactory addressFactory, MessageFactory messageFactory, HeaderFactory headerFactory, SipProvider sipProvider, ListeningPoint listeningPoint) {
        Request request = requestEvent.getRequest();
        if (null == request) {
            log.error("processRequest RequestEvent is null");
            return;
        }
        switch (request.getMethod().toUpperCase()) {
            case Request.MESSAGE:
                log.debug("收到MESSAGE的请求");
                doRequestMessage(requestEvent, addressFactory, messageFactory, headerFactory, sipProvider);
                break;
            case Request.REGISTER:
                log.info("收到下级域REGISTER的请求");
                doRequestRegister(requestEvent, addressFactory, messageFactory, headerFactory, sipProvider);
                break;
            case Request.ACK:
                doRequestAsk(requestEvent, addressFactory, messageFactory, headerFactory, sipProvider);
                break;
            case Request.BYE:
                log.info("收到BYE的请求");
                doRequestBye(requestEvent, addressFactory, messageFactory, headerFactory, sipProvider);
                break;
            case Request.INVITE:
                log.info("Incoming call...");
                doRequestInvite(requestEvent, request, addressFactory, messageFactory, headerFactory, sipProvider, listeningPoint);
                break;
            default:
                log.info("不处理的requestMethod：" + request.getMethod().toUpperCase());
        }
    }

    @Override
    public void processResponse(ResponseEvent responseEvent, AddressFactory addressFactory, MessageFactory messageFactory, HeaderFactory headerFactory, SipProvider sipProvider) throws InvalidArgumentException {
        Response response = responseEvent.getResponse();
        if (response.getStatusCode() == Response.TRYING) {
            log.info("收到的response is 100 TRYING");
            return;
        }
        try {
            CSeqHeader cSeqHeader = (CSeqHeader) response.getHeader(CSeqHeader.NAME);
            switch (cSeqHeader.getMethod().toUpperCase()) {
                case Request.MESSAGE:
                    log.debug("收到MESSAGE的返回");
                    doResponseMessage(responseEvent, messageFactory, headerFactory, sipProvider);
                    break;
                case Request.REGISTER:
                    log.info("收到REGISTER的返回");
                    doResponseRegister(responseEvent, addressFactory, messageFactory, headerFactory, sipProvider, response);
                    break;
                case Request.INVITE:
                    log.info("收到INVITE的返回");
                    doResponseInvite(responseEvent, addressFactory, messageFactory, headerFactory, sipProvider, response, cSeqHeader);
                    break;
                case Request.ACK:
                    log.info("收到ACK的返回");
                    break;
                case Request.BYE:
                    log.info("收到BYE的返回");
                    doResponseBye(responseEvent, addressFactory, messageFactory, headerFactory, sipProvider, response);
                    break;
                default:
                    log.info("不处理的requestMethod：" + cSeqHeader.getMethod().toUpperCase());
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void doResponseInvite(ResponseEvent responseEvent, AddressFactory addressFactory, MessageFactory messageFactory, HeaderFactory headerFactory, SipProvider sipProvider, Response response, CSeqHeader cSeqHeader) throws ParseException, SipException, InvalidArgumentException {
        if (responseEvent.getResponse().getStatusCode() == Response.OK) {
            log.info("收到INVITE的OK返回，组装发送ACK信令");
            sendAckRequest(responseEvent, messageFactory, addressFactory, headerFactory, sipProvider, response);
        } else {
            log.info("异常的INVITE返回，返回编码：{}", responseEvent.getResponse().getStatusCode());
        }
    }

    private void sendAckRequest(ResponseEvent responseEvent, MessageFactory messageFactory, AddressFactory addressFactory, HeaderFactory headerFactory, SipProvider sipProvider, Response response) throws InvalidArgumentException, ParseException, SipException {
        CSeqHeader cSeqHeader = (CSeqHeader) response.getHeader(CSeqHeader.NAME);
        CallIdHeader callIdHeader = ((CallIdHeader) response.getHeader(CallIdHeader.NAME));
        ToHeader toHeader = ((ToHeader) response.getHeader(ToHeader.NAME));
        FromHeader fromHeader = ((FromHeader) response.getHeader(FromHeader.NAME));
        SipURI requestURI = (SipURI) toHeader.getAddress().getURI();
        MaxForwardsHeader maxForwards = headerFactory.createMaxForwardsHeader(70);
        ViaHeader viaHeader = ((ViaHeader) response.getHeader(ViaHeader.NAME));
        viaHeader.setRPort();
        ArrayList<ViaHeader> viaHeaders = new ArrayList<ViaHeader>();
        viaHeaders.add(viaHeader);
        cSeqHeader.setMethod(Request.ACK);
        Request ACKRequest = messageFactory.createRequest(requestURI, Request.ACK, callIdHeader, cSeqHeader, fromHeader, toHeader, viaHeaders, maxForwards);
        sipProvider.sendRequest(ACKRequest);
    }

    private void doRequestInvite(RequestEvent requestEvent, AddressFactory addressFactory, MessageFactory messageFactory,
                                 HeaderFactory headerFactory, SipProvider sipProvider) {
    }

    private void parseSdp(String sdpData) {
        try {
            SdpFactory sdpFactory = SdpFactory.getInstance();
            SessionDescription sessionDescription = sdpFactory.createSessionDescription(sdpData);

            // 获取所有媒体描述
            Vector mediaDescriptions = sessionDescription.getMediaDescriptions(true);
            for (int i = 0; i < mediaDescriptions.size(); i++) {
                MediaDescription mediaDescription = (MediaDescription) mediaDescriptions.get(i);
                log.info("mediaDescription:{}", JSONUtil.toJsonStr(mediaDescriptions));
                Media media = mediaDescription.getMedia();

                // 提取媒体类型、端口号和传输协议
                String mediaType = media.getMediaType();
                int port = media.getMediaPort();
                String protocol = media.getProtocol();

                System.out.println("Media Type: " + mediaType + ", Port: " + port + ", Protocol: " + protocol);
            }
        } catch (SdpParseException e) {
            e.printStackTrace();
        } catch (SdpException e) {
            throw new RuntimeException(e);
        }
    }

    private void doRequestInvite(RequestEvent requestEvent, Request request, AddressFactory addressFactory, MessageFactory messageFactory,
                                 HeaderFactory headerFactory, SipProvider sipProvider, ListeningPoint listeningPoint) {
        log.info("进入处理");
        log.info("request====>:{}", JSONUtil.toJsonStr(request));
        log.info("requestURI====>:{}", request.getRequestURI());
        log.info("requestEvent====>:{}", JSONUtil.toJsonStr(requestEvent));

        ContentTypeHeader contentTypeHeader = (ContentTypeHeader) request.getHeader(ContentTypeHeader.NAME);
        if (contentTypeHeader != null && contentTypeHeader.getContentType().equalsIgnoreCase("application") &&
                contentTypeHeader.getContentSubType().equalsIgnoreCase("sdp")) {

            // 获取 SDP 信息
            try {
                byte[] rawContent = request.getRawContent();
                String sdpContent = new String(rawContent, "UTF-8");
                SessionDescription sdp = SdpFactory.getInstance().createSessionDescription(sdpContent);

                // 获取媒体描述，假设只有一个音频描述
                MediaDescription mediaDescription = (MediaDescription) sdp.getMediaDescriptions(false).get(0);
                Media media = mediaDescription.getMedia();

                remotePort = media.getMediaPort();
                String remoteIp = sdp.getConnection().getAddress();
                log.info("===========>remoteRtpPort:{},ip:{}", remotePort, remoteIp);
            } catch (SdpParseException e) {
                throw new RuntimeException(e);
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            } catch (SdpException e) {
                throw new RuntimeException(e);
            }
        }


        SIPRequest sipRequest = (SIPRequest) requestEvent.getRequest();
        sipRequest.getRawContent();
        String requesterId = SipUtils.getUserIdFromFromHeader(sipRequest);
        CallIdHeader callIdHeader = (CallIdHeader) sipRequest.getHeader(CallIdHeader.NAME);
        log.info("[INVITE] requesterId: {}, callId: {}, 来自：{}：{}",
                requesterId, callIdHeader.getCallId(), sipRequest.getRemoteAddress(), sipRequest.getRemotePort());
        log.info("=====>sipRequest:{}", JSONUtil.toJsonStr(sipRequest));
        Response response = null;
        try {
            log.info("创建一个ringing响应");
            response = messageFactory.createResponse(Response.RINGING, request);
            ToHeader toHeader = (ToHeader) response.getHeader(ToHeader.NAME);
            toHeader.setTag("4321"); // 设置一个标签
            //在获得response对象后，我们可以将其发送到网络上。首先，使用SipProvider的getNewServerTransaction()方法获取与此请求对应的服务器事务。在使用该方法时，需要提供接收到的request信息。
            ServerTransaction serverTransaction = sipProvider.getNewServerTransaction(request);
            // 最后，使用 ServerTransaction 的 sendResponse() 方法将响应发送到网络上。在使用时需要提供要发送的响应。
            serverTransaction.sendResponse(response);
            log.info("已发送Ringing响应");


            // 在发送完180/Ringing响应后，接下来要建立200/Ok响应。建立200/Ok响应的方法和180/Ringing响应大致相同。
            response = messageFactory.createResponse(Response.OK, request);
            log.info("创建一个OK响应");
            extracted(request, addressFactory, headerFactory, response);


            //我们使用之前建立的ServerTransaction对象将200/OK消息发送到网络上。
            log.info("发送OK的serverTransaction:{}", serverTransaction);
            serverTransaction.sendResponse(response);
            log.info("已发送OK响应");

        } catch (ParseException e) {
            throw new RuntimeException(e);
        } catch (InvalidArgumentException e) {
            throw new RuntimeException(e);
        } catch (TransactionUnavailableException e) {
            throw new RuntimeException(e);
        } catch (SipException e) {
            throw new RuntimeException(e);
        }


    }

    private static void extracted(Request request, AddressFactory addressFactory, HeaderFactory headerFactory, Response response) throws ParseException {
        ToHeader toHeader;
        // 获得 toHeader 对象
        toHeader = (ToHeader) response.getHeader(ToHeader.NAME);
        toHeader.setTag("4321");
        // Via 头部 - 通常从请求中复制
        ViaHeader viaHeader = (ViaHeader) request.getHeader(ViaHeader.NAME);
        response.addHeader(viaHeader);

        // From 头部 - 从请求中复制
        FromHeader fromHeader = (FromHeader) request.getHeader(FromHeader.NAME);
        response.addHeader(fromHeader);

        // To 头部 - 从请求中复制并添加标签
        response.addHeader(toHeader);

        // Call-ID 头部 - 从请求中复制
        CallIdHeader callIdHeader = (CallIdHeader) request.getHeader(CallIdHeader.NAME);
        response.addHeader(callIdHeader);

        // CSeq 头部 - 从请求中复制
        CSeqHeader cSeqHeader = (CSeqHeader) request.getHeader(CSeqHeader.NAME);
        response.addHeader(cSeqHeader);

        // Contact 头部 - 通常包含 UAS 的 URI

        // 设置 Contact 头部
        SipURI contactURI = addressFactory.createSipURI("user", "47.99.40.56:5060");
        contactURI.setTransportParam("udp");
        Address contactAddress = addressFactory.createAddress(contactURI);
        ContactHeader contactHeader = headerFactory.createContactHeader(contactAddress);
        response.addHeader(contactHeader);


        // 构造 SDP 数据
        // 根据需要替换 IP 和其他 SDP 参数
        String sdpData = "v=0\r\n" //必选，SDP协议版本号
                + "o=- 6789 0 IN IP4 47.99.40.56\r\n" //必选，创建者，会话信息
                + "s=-\r\n" //必选，session名称
                + "c=IN IP4 47.99.40.56\r\n" //可选，媒体链接信息
                + "t=0 0\r\n" //必选，会话的开始时间和结束时间。单位为秒。
                + "m=audio 8830 RTP/AVP 0 8 101\r\n" //必选，一个会话描述包含几个媒体描述
                + "a=rtpmap:0 PCMU/8000\r\n" //0或多个会话属性行
                + "a=rtpmap:8 PCMA/8000\r\n"
                + "a=rtpmap:101 telephone-event/8000\r\n"
                + "a=sendrecv\r\n";

        ContentTypeHeader contentTypeHeader = headerFactory.createContentTypeHeader("application", "sdp");
        response.setContent(sdpData, contentTypeHeader);


    }


    private void doRequestBye(RequestEvent requestEvent, AddressFactory addressFactory, MessageFactory messageFactory, HeaderFactory headerFactory, SipProvider sipProvider) {
        Request request = requestEvent.getRequest();
        ServerTransaction serverTransaction = requestEvent.getServerTransaction();


        // 创建并发送 200 OK 响应
        Response response = null;
        try {
            if (serverTransaction == null) {
                serverTransaction = sipProvider.getNewServerTransaction(request);
            }
            response = messageFactory.createResponse(Response.OK, request);
            serverTransaction.sendResponse(response);
            log.info("Sent 200 OK in response to BYE");
        } catch (ParseException e) {
            throw new RuntimeException(e);
        } catch (TransactionAlreadyExistsException e) {
            throw new RuntimeException(e);
        } catch (TransactionUnavailableException e) {
            throw new RuntimeException(e);
        } catch (InvalidArgumentException e) {
            throw new RuntimeException(e);
        } catch (SipException e) {
            throw new RuntimeException(e);
        }
        // 进行任何必要的清理工作
        rtpSession.stop();
        captureRtpSender.stop();
    }

    private void doResponseRegister(ResponseEvent responseEvent, AddressFactory addressFactory, MessageFactory messageFactory, HeaderFactory headerFactory, SipProvider sipProvider, Response response) {
    }

    private void doResponseMessage(ResponseEvent responseEvent, MessageFactory messageFactory, HeaderFactory headerFactory, SipProvider sipProvider) {
    }

    @Override
    public void processError(String errorMessage) {

    }

    private void doResponseBye(ResponseEvent responseEvent, AddressFactory addressFactory, MessageFactory messageFactory, HeaderFactory headerFactory, SipProvider sipProvider, Response response) {
    }

    private IncomingRtpReader incomingRtpReader;

    private void doRequestAsk(RequestEvent requestEvent, AddressFactory addressFactory, MessageFactory messageFactory, HeaderFactory headerFactory, SipProvider sipProvider) {
        log.info("==========>收到ACK的请求");
        // WAV文件路径
//        String wavFilePath = "https://buff8.oss-cn-shanghai.aliyuncs.com/lcc.wav";
        String wavFilePath = "/usr/local/tools/lcc.wav";
        // WAV文件路径
//        UrlReader fileReader = new UrlReader(wavFilePath, logger);
        FileReader fileReader = new FileReader(wavFilePath, logger);
        log.info("初始化FileReader........");
        fileReader.init();
        String localAddress = "47.99.40.56";
        String remoteAddress = "47.96.76.228";
//        int remotePort = 8830;

        InetAddress inetAddress;
        try {
            log.info("======>创建inetAddress");
            inetAddress = InetAddress.getByName(localAddress);
        } catch (UnknownHostException e) {
            log.error("unknown host: " + localAddress, e);
            return;
        }
        log.info("======>开始创建RtpSession");
        //创建rpt会话

        try {
            //和sdp中声明的端口一致
            log.info("==========>判断datagramSocket是否关闭,datagramSocket是否为空:{}", Objects.isNull(datagramSocket));
            if (Objects.isNull(datagramSocket)) {
                datagramSocket = new DatagramSocket(8830);
            } else {
                log.info("==========>判断datagramSocket是否关闭,是否关闭:{}", datagramSocket.isClosed());
            }
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }
        rtpSession = new RtpSession(inetAddress, datagramSocket,
                false, logger, ".", fileReader);

        log.info("======>创建rpt会话");
        try {
            inetAddress = InetAddress.getByName(remoteAddress);
            rtpSession.setRemoteAddress(inetAddress);
        } catch (UnknownHostException e) {
            log.error("unknown host: " + remoteAddress, e);
        }
        rtpSession.setRemotePort(remotePort);

        Codec codec = new Codec();
        codec.setPayloadType(8);
        codec.setName("PCMU");

        try {
            //捕获实时传输sender,第一次调用
            //组织发送参数
            captureRtpSender = new CaptureRtpSender(rtpSession,
                    fileReader, false, codec, logger,
                    ".");
            log.info("======>创建rptSender会话");
            incomingRtpReader = new IncomingRtpReader(
                    captureRtpSender.getRtpSession(), null, codec,
                    logger);
            log.info("======>创建rptReader会话");
        } catch (IOException e) {
            logger.error("input/output error", e);
            return;
        }

        try {
            rtpSession.start();
            captureRtpSender.start();
            incomingRtpReader.start();
            log.info("线程启动成功....");
        } catch (IOException e) {
            logger.error("input/output error", e);
        }
    }

    private void doRequestRegister(RequestEvent requestEvent, AddressFactory addressFactory, MessageFactory messageFactory, HeaderFactory headerFactory, SipProvider sipProvider) {
        Request request = requestEvent.getRequest();
        try {
            String deviceId = getDeviceIdByRequest(request);
            log.info("Register deviceId is {}, toURI is {}", deviceId);
            if (StringUtils.isEmpty(deviceId)) {
                log.error("Register error, deviceId is empty!");
                return;
            }
            //无需鉴权或者鉴权判断通过
            if (isAuthClosed(deviceId) || isAuthorizationPass(request)) {
                //返回成功 返回Response.OK
                log.info("Register doSuccess!");
                doSuccess(requestEvent, addressFactory, messageFactory, headerFactory, sipProvider);
            } else if (isRegisterWithoutAuth(request)) {
                doUnAuthorized401(requestEvent, messageFactory, headerFactory, sipProvider, request, deviceId);
            } else {
                doLoginFail403(requestEvent, addressFactory, messageFactory, headerFactory, sipProvider);
            }
        } catch (Exception e) {
            log.error("处理Register请求的时候出错 error, {}", e.getMessage());
            e.printStackTrace();
        }
    }

    private void doRequestMessage(RequestEvent requestEvent, AddressFactory addressFactory, MessageFactory messageFactory, HeaderFactory headerFactory, SipProvider sipProvider) {
        try {
            Request request = requestEvent.getRequest();
            String encode = request.toString();
            if (org.apache.commons.lang3.StringUtils.contains(encode, MST_TYPE_KEEPALIVE)) {
                log.debug("收到下级域发来的心跳请求,{}", request.getRequestURI());
                doSuccess(requestEvent, addressFactory, messageFactory, headerFactory, sipProvider);
            } else if (org.apache.commons.lang3.StringUtils.contains(encode, MST_TYPE_CATALOG)) {
                ViaHeader viaHeader = (ViaHeader) request.getHeader(ViaHeader.NAME);
                log.info("收到目录检索返回，IP={},Port={}", viaHeader.getReceived(), viaHeader.getRPort());
                doSuccess(requestEvent, addressFactory, messageFactory, headerFactory, sipProvider);
                doRequestCatalog(requestEvent.getRequest(), encode);
            }
        } catch (Exception e) {
            log.error("doRequestMessage Error:{}", e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 组装登录成功200的Response
     * @return
     */
    private void doSuccess(RequestEvent requestEvent, AddressFactory addressFactory, MessageFactory messageFactory, HeaderFactory headerFactory, SipProvider sipProvider) throws ParseException, SipException, InvalidArgumentException {
        Request request = requestEvent.getRequest();
        Response response = messageFactory.createResponse(Response.OK, request);
        DateHeader dateHeader = headerFactory.createDateHeader(Calendar.getInstance());
        response.addHeader(dateHeader);
        ServerTransaction serverTransactionId = requestEvent.getServerTransaction() == null ? sipProvider.getNewServerTransaction(request) : requestEvent.getServerTransaction();
        serverTransactionId.sendResponse(response);
    }

    /**
     * 下级域发送过来的catalog目录返回命令
     * @param request
     * @param encode
     */
    private void doRequestCatalog(Request request, String encode) {
        String msgXml = new StringBuffer(XML_HEAD).append(org.apache.commons.lang3.StringUtils.substringAfterLast(encode, XML_HEAD)).toString();
        try {
            msgXml = new String((byte[]) request.getContent(), "GB2312");
            log.info(msgXml);
        } catch (Exception e) {
            log.debug("saveCatalog error, msgXml: {}", msgXml);
            e.printStackTrace();
        }
    }

    /**
     * 组装登录失败403的Response
     * @return
     */
    private void doLoginFail403(RequestEvent requestEvent, AddressFactory addressFactory, MessageFactory messageFactory, HeaderFactory headerFactory, SipProvider sipProvider) throws ParseException, SipException, InvalidArgumentException {
        Request request = requestEvent.getRequest();
        Response response = messageFactory.createResponse(Response.FORBIDDEN, request);
        DateHeader dateHeader = headerFactory.createDateHeader(Calendar.getInstance());
        response.addHeader(dateHeader);
        ServerTransaction serverTransactionId = requestEvent.getServerTransaction() == null ? sipProvider.getNewServerTransaction(request) : requestEvent.getServerTransaction();
        serverTransactionId.sendResponse(response);
    }

    private String getDeviceIdByRequest(Request request) {
        ToHeader toHead = (ToHeader) request.getHeader(ToHeader.NAME);
        SipURI toUri = (SipURI) toHead.getAddress().getURI();
        return toUri.getUser();
    }

    /**
     * 判断鉴权是否关闭
     * @param deviceId 设备ID
     * @return
     */
    private boolean isAuthClosed(String deviceId) {
        return ("34020000001110000001".equals(deviceId));
    }

    /**
     * 是否校验鉴权通过
     * @param request
     * @return true 通过
     */
    private boolean isAuthorizationPass(Request request) {
        if (isRegisterWithoutAuth(request)) {
            return false;
        }
        AuthorizationHeader authorizationHeader = (AuthorizationHeader) request.getHeader(AuthorizationHeader.NAME);
        String username = authorizationHeader.getUsername();
        String realm = authorizationHeader.getRealm();
        String nonce = authorizationHeader.getNonce();
        URI uri = authorizationHeader.getURI();
        String res = authorizationHeader.getResponse();
        String algorithm = authorizationHeader.getAlgorithm();
        log.info("Authorization信息：username=" + username + ",realm=" + realm + ",nonce=" + nonce + ",uri=" + uri + ",response=" + res + ",algorithm=" + algorithm);
        if (null == username || null == realm || null == nonce || null == uri || null == res || null == algorithm) {
            log.info("Authorization信息不全，无法认证。");
            return false;
        } else {
            // 比较Authorization信息正确性
            String A1 = MD5Util.MD5(username + ":" + realm + ":" + password);
            String A2 = MD5Util.MD5("REGISTER" + ":" + uri);
            String resStr = MD5Util.MD5(A1 + ":" + nonce + ":" + A2);
            return resStr.equals(res);
        }
    }


    private void doUnAuthorized401(RequestEvent requestEvent, MessageFactory messageFactory, HeaderFactory headerFactory, SipProvider sipProvider, Request request, String deviceId) throws ParseException, SipException, InvalidArgumentException {
        Response response;
        response = messageFactory.createResponse(Response.UNAUTHORIZED, request);
        String realm = generateShortUUID();

        String callId = getCallIdFromRequest(request);
        String nonce = MD5Util.MD5(callId + deviceId);
        WWWAuthenticateHeader wwwAuthenticateHeader = headerFactory.createWWWAuthenticateHeader("Digest realm=\"" + realm + "\",nonce=\"" + nonce + "\",algorithm=MD5");
        response.setHeader(wwwAuthenticateHeader);
        ServerTransaction serverTransactionId = requestEvent.getServerTransaction() == null ? sipProvider.getNewServerTransaction(request) : requestEvent.getServerTransaction();
        serverTransactionId.sendResponse(response);
    }


    /**
     * 没有Auth信息，一般在第一次Register的时候
     *
     * @param request
     * @return
     */
    private boolean isRegisterWithAuth(Request request) {
        int expires = request.getExpires().getExpires();
        AuthorizationHeader authorizationHeader = (AuthorizationHeader) request.getHeader(AuthorizationHeader.NAME);
        return expires > 0 && authorizationHeader != null;
    }

    /**
     * 有Auth信息，一般在第二次Register的时候，这个时候会带着第一次服务端返回的Digest信息
     *
     * @param request
     * @return
     */
    private boolean isRegisterWithoutAuth(Request request) {
        int expires = request.getExpires().getExpires();
        AuthorizationHeader authorizationHeader = (AuthorizationHeader) request.getHeader(AuthorizationHeader.NAME);
        return expires > 0 && authorizationHeader == null;
    }

    private String getCallIdFromRequest(Request request) {
        CallIdHeader callIdHeader = (CallIdHeader) request.getHeader(CallIdHeader.NAME);
        return callIdHeader.getCallId();
    }


    public static String generateShortUUID() {
        StringBuffer shortBuffer = new StringBuffer();
        String uuid = UUID.randomUUID().toString().replace("-", "");
        for (int i = 0; i < 8; i++) {
            String str = uuid.substring(i * 4, i * 4 + 4);
            int x = Integer.parseInt(str, 16);
            shortBuffer.append(chars[x % 0x3E]);
        }
        return shortBuffer.toString();
    }

    public static String[] chars = new String[]{"a", "b", "c", "d", "e", "f",
            "g", "h", "i", "j", "k", "l", "m", "n", "o", "p", "q", "r", "s",
            "t", "u", "v", "w", "x", "y", "z", "0", "1", "2", "3", "4", "5",
            "6", "7", "8", "9", "A", "B", "C", "D", "E", "F", "G", "H", "I",
            "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V",
            "W", "X", "Y", "Z"};
}
