package com.net.sipcall.sipcalling.service.demo;

import gov.nist.javax.sip.address.SipUri;
import gov.nist.javax.sip.header.CSeq;
import gov.nist.javax.sip.header.Contact;
import gov.nist.javax.sip.header.ContentLength;
import gov.nist.javax.sip.header.ContentType;
import gov.nist.javax.sip.header.From;
import gov.nist.javax.sip.header.Via;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.peers.Logger;
import net.sourceforge.peers.media.AbstractSoundManager;
import net.sourceforge.peers.media.CaptureRtpSender;
import net.sourceforge.peers.media.FileReader;
import net.sourceforge.peers.rtp.RtpSession;
import net.sourceforge.peers.sdp.Codec;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.ParseException;
import java.util.*;
import javax.sip.*;
import javax.sip.address.Address;
import javax.sip.address.AddressFactory;
import javax.sip.address.SipURI;
import javax.sip.address.URI;
import javax.sip.header.*;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;

@Slf4j
public class SipPhone implements SipListener {
    public void processDialogTerminated(DialogTerminatedEvent arg0) {
        // TODO Auto-generated method stub
        System.out.println("processDialogTerminated " + arg0.toString());
    }

    public void processIOException(IOExceptionEvent arg0) {
        // TODO Auto-generated method stub
        System.out.println("processIOException " + arg0.toString());
    }

    /**
     * 保存当前注册的用户
     */
    private static Hashtable<URI, URI> currUser = new Hashtable();


    /**
     * @author software
     * 注册定时器
     */
    class TimerTask extends Timer {
        /**
         * default constructor
         */
        public TimerTask() {

        }

        /**
         *  如果定时任务到，则删除该用户的注册信息
         */
        public void run() {

        }
    }


    /**
     * 服务器侦听IP地址
     */
    private String ipAddr = "47.99.40.56";


    /**
     * 服务器侦听端口
     */
    private int port = 5060;

    /**
     * 处理register请求
     * @param request 请求消息
     */
    private void processRegister(Request request, RequestEvent requestEvent) {
        log.info("===========================>进入processRegister");
        if (null == request) {
            System.out.println("processInvite request is null.");
            return;
        }
        //System.out.println("Request " + request.toString());
        ServerTransaction serverTransactionId = requestEvent.getServerTransaction();

        try {
            Response response = null;
            ToHeader head = (ToHeader) request.getHeader(ToHeader.NAME);
            Address toAddress = head.getAddress();
            URI toURI = toAddress.getURI();
            ContactHeader contactHeader = (ContactHeader) request.getHeader("Contact");
            Address contactAddr = contactHeader.getAddress();
            URI contactURI = contactAddr.getURI();
            System.out.println("processRegister from: " + toURI + " request str: " + contactURI);
            int expires = request.getExpires().getExpires();
            // 如果expires不等于0,则为注册，否则为注销。
            if (expires != 0 || contactHeader.getExpires() != 0) {
                currUser.put(toURI, contactURI);
                System.out.println("register user " + toURI);
            } else {
                currUser.remove(toURI);
                System.out.println("unregister user " + toURI);
            }

            response = msgFactory.createResponse(200, request);
            System.out.println("send register response  : " + response.toString());

            if (serverTransactionId == null) {
                serverTransactionId = sipProvider.getNewServerTransaction(request);
                serverTransactionId.sendResponse(response);
                //serverTransactionId.terminate();
                System.out.println("register serverTransaction: " + serverTransactionId);
            } else {
                System.out.println("processRequest serverTransactionId is null.");
            }

        } catch (ParseException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (SipException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (InvalidArgumentException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    /**
     * 处理invite请求
     * @param request 请求消息
     */
    private void processInvite(Request request, RequestEvent requestEvent) {
        log.info("===========================>进入processRegister");
        if (null == request) {
            System.out.println("processInvite request is null.");
            return;
        }
        Response response = null;
        try {
            log.info("创建一个ringing响应");
            response = msgFactory.createResponse(Response.RINGING, request);
            ToHeader toHeader = (ToHeader) response.getHeader(ToHeader.NAME);
            toHeader.setTag("4321"); // 设置一个标签
            //在获得response对象后，我们可以将其发送到网络上。首先，使用SipProvider的getNewServerTransaction()方法获取与此请求对应的服务器事务。在使用该方法时，需要提供接收到的request信息。
            ServerTransaction serverTransaction = sipProvider.getNewServerTransaction(request);
            // 最后，使用 ServerTransaction 的 sendResponse() 方法将响应发送到网络上。在使用时需要提供要发送的响应。
            serverTransaction.sendResponse(response);
            log.info("已发送Ringing响应");


            // 在发送完180/Ringing响应后，接下来要建立200/Ok响应。建立200/Ok响应的方法和180/Ringing响应大致相同。
            response = msgFactory.createResponse(Response.OK, request);
            log.info("创建一个OK响应");
            extracted(request, addressFactory, headerFactory, response);


            //我们使用之前建立的ServerTransaction对象将200/OK消息发送到网络上。
            log.info("发送OK的serverTransaction:{}", serverTransaction);
            serverTransaction.sendResponse(response);
            log.info("已发送OK响应");
        } catch (TransactionUnavailableException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        } catch (SipException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (ParseException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
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
        String sdpData = "v=0\r\n"
                + "o=- 12345 67890 IN IP4 47.99.40.56\r\n" // 根据需要替换 IP 和其他 SDP 参数
                + "s=-\r\n"
                + "c=IN IP4 47.99.40.56\r\n"
                + "t=0 0\r\n"
                + "m=audio 49170 RTP/AVP 0 8 101\r\n"
                + "a=rtpmap:0 PCMU/8000\r\n"
                + "a=rtpmap:8 PCMA/8000\r\n"
                + "a=rtpmap:101 telephone-event/8000\r\n"
                + "a=sendrecv\r\n";

        ContentTypeHeader contentTypeHeader = headerFactory.createContentTypeHeader("application", "sdp");
        response.setContent(sdpData, contentTypeHeader);


    }

    /**
     * 处理SUBSCRIBE请求
     * @param request 请求消息
     */
    private void processSubscribe(Request request) {
        log.info("===========================>processSubscribe");
        if (null == request) {
            System.out.println("processSubscribe request is null.");
            return;
        }
        ServerTransaction serverTransactionId = null;
        try {
            serverTransactionId = sipProvider.getNewServerTransaction(request);
        } catch (TransactionAlreadyExistsException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        } catch (TransactionUnavailableException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }

        try {
            Response response = null;
            response = msgFactory.createResponse(200, request);
            if (response != null) {
                ExpiresHeader expireHeader = headerFactory.createExpiresHeader(30);
                response.setExpires(expireHeader);
            }
            System.out.println("response : " + response.toString());

            if (serverTransactionId != null) {
                serverTransactionId.sendResponse(response);
                serverTransactionId.terminate();
            } else {
                System.out.println("processRequest serverTransactionId is null.");
            }

        } catch (ParseException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (SipException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (InvalidArgumentException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    /**
     * 处理BYE请求
     * @param request 请求消息
     */
    private void processBye(Request request, RequestEvent requestEvent) {
        log.info("===========================>processBye");
        if (null == request || null == requestEvent) {
            System.out.println("processBye request is null.");
            return;
        }
        Request byeReq = null;
        Dialog dialog = requestEvent.getDialog();
        System.out.println("calleeDialog : " + calleeDialog);
        System.out.println("callerDialog : " + callerDialog);
        try {
            if (dialog.equals(calleeDialog)) {
                byeReq = callerDialog.createRequest(request.getMethod());
                ClientTransaction clientTran = sipProvider.getNewClientTransaction(byeReq);
                callerDialog.sendRequest(clientTran);
                calleeDialog.setApplicationData(requestEvent.getServerTransaction());
            } else if (dialog.equals(callerDialog)) {
                byeReq = calleeDialog.createRequest(request.getMethod());
                ClientTransaction clientTran = sipProvider.getNewClientTransaction(byeReq);
                calleeDialog.sendRequest(clientTran);
                callerDialog.setApplicationData(requestEvent.getServerTransaction());
            } else {
                System.out.println("");
            }

            System.out.println("send bye to peer:" + byeReq.toString());
        } catch (SipException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }

    /**
     * 处理CANCEL请求
     * @param request 请求消息
     */
    private void processCancel(Request request) {
        if (null == request) {
            System.out.println("processCancel request is null.");
            return;
        }
    }

    /**
     * 处理INFO请求
     * @param request 请求消息
     */
    private void processInfo(Request request) {
        if (null == request) {
            System.out.println("processInfo request is null.");
            return;
        }
    }
    private RtpSession rtpSession;
    private DatagramSocket datagramSocket;

    private Logger logger;

    private CaptureRtpSender captureRtpSender;

    /**
     * 处理ACK请求
     * @param request 请求消息
     */
    private void processAck(Request request, RequestEvent requestEvent) {
        log.info("===========================>processAck");
        Dialog dialog = requestEvent.getDialog();
        CSeq csReq = (CSeq)request.getHeader(CSeq.NAME);
        try {
            Request ack = dialog.createAck(csReq.getSeqNumber());
            dialog.sendAck(ack);
        } catch (InvalidArgumentException e) {
            throw new RuntimeException(e);
        } catch (SipException e) {
            throw new RuntimeException(e);
        }
        /*// WAV文件路径
        String wavFilePath = "lcc.wav";
        AbstractSoundManager soundManager = null;
        soundManager.init();
        String localAddress = "47.99.40.56";
        String remoteAddress = "47.96.76.228";
        int remotePort = 15680;

        InetAddress inetAddress;
        try {
            inetAddress = InetAddress.getByName(localAddress);
        } catch (UnknownHostException e) {
            log.error("unknown host: " + localAddress, e);
            return;
        }
        //创建rpt会话
        rtpSession = new RtpSession(inetAddress, datagramSocket,
                false, logger, ".", soundManager);

        try {
            inetAddress = InetAddress.getByName(remoteAddress);
            rtpSession.setRemoteAddress(inetAddress);
        } catch (UnknownHostException e) {
            log.error("unknown host: " + remoteAddress, e);
        }
        rtpSession.setRemotePort(remotePort);

        Codec codec = new Codec();

        FileReader fileReader = new FileReader(wavFilePath, logger);
        fileReader.init();
        try {
            //捕获实时传输sender,第一次调用
            //组织发送参数
            captureRtpSender = new CaptureRtpSender(rtpSession,
                    fileReader, false, codec, logger,
                    ".");
        } catch (IOException e) {
            logger.error("input/output error", e);
            return;
        }

        try {
            captureRtpSender.start();
        } catch (IOException e) {
            logger.error("input/output error", e);
        }*/


    }

    /**
     * 处理CANCEL消息
     * @param request
     * @param requestEvent
     */
    private void processCancel(Request request, RequestEvent requestEvent) {
        log.info("===========================>processCancel");
        // 判断参数是否有效
        if (request == null || requestEvent == null) {
            System.out.println("processCancel input parameter invalid.");
            return;
        }

        try {
            // 发送CANCEL 200 OK消息
            Response response = msgFactory.createResponse(Response.OK, request);
            ServerTransaction cancelServTran = requestEvent.getServerTransaction();
            if (cancelServTran == null) {
                cancelServTran = sipProvider.getNewServerTransaction(request);
            }
            cancelServTran.sendResponse(response);

            // 向对端发送CANCEL消息
            Request cancelReq = null;
            Request inviteReq = clientTransactionId.getRequest();
            List list = new ArrayList();
            Via viaHeader = (Via) inviteReq.getHeader(Via.NAME);
            list.add(viaHeader);

            CSeq cseq = (CSeq) inviteReq.getHeader(CSeq.NAME);
            CSeq cancelCSeq = (CSeq) headerFactory.createCSeqHeader(cseq.getSeqNumber(), Request.CANCEL);
            cancelReq = msgFactory.createRequest(inviteReq.getRequestURI(),
                    inviteReq.getMethod(),
                    (CallIdHeader) inviteReq.getHeader(CallIdHeader.NAME),
                    cancelCSeq,
                    (FromHeader) inviteReq.getHeader(From.NAME),
                    (ToHeader) inviteReq.getHeader(ToHeader.NAME),
                    list,
                    (MaxForwardsHeader) inviteReq.getHeader(MaxForwardsHeader.NAME));
            ClientTransaction cancelClientTran = sipProvider.getNewClientTransaction(cancelReq);
            cancelClientTran.sendRequest();
        } catch (ParseException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (TransactionAlreadyExistsException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (TransactionUnavailableException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (SipException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (InvalidArgumentException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }


    private ServerTransaction serverTransactionId = null;

    /* (non-Javadoc)
     * @see javax.sip.SipListener#processRequest(javax.sip.RequestEvent)
     */
    @Override
    public void processRequest(RequestEvent arg0) {
        log.info("=====================>进入processRequest:{},\nmethod:{}", arg0.getRequest().toString(), arg0.getRequest().getMethod().toString());
        Request request = arg0.getRequest();
        if (null == request) {
            System.out.println("processRequest request is null.");
            return;
        }
        System.out.println("processRequest:" + request.toString());

        if (Request.INVITE.equals(request.getMethod())) {
            processInvite(request, arg0);
        } else if (Request.REGISTER.equals(request.getMethod())) {
            processRegister(request, arg0);
        } else if (Request.SUBSCRIBE.equals(request.getMethod())) {
            processSubscribe(request);
        } else if (Request.ACK.equalsIgnoreCase(request.getMethod())) {
            processAck(request, arg0);
        } else if (Request.BYE.equalsIgnoreCase(request.getMethod())) {
            processBye(request, arg0);
        } else if (Request.CANCEL.equalsIgnoreCase(request.getMethod())) {
            processCancel(request, arg0);
        } else {
            System.out.println("no support the method!");
        }
    }

    /**
     * 主叫对话
     */
    private Dialog calleeDialog = null;

    /**
     * 被叫对话
     */
    private Dialog callerDialog = null;

    /**
     *
     */
    ClientTransaction clientTransactionId = null;

    /**
     * 处理BYE响应消息
     * @param reponseEvent
     */
    private void doByeResponse(Response response, ResponseEvent responseEvent) {
        Dialog dialog = responseEvent.getDialog();

        try {
            Response byeResp = null;
            if (callerDialog.equals(dialog)) {
                ServerTransaction servTran = (ServerTransaction) calleeDialog.getApplicationData();
                byeResp = msgFactory.createResponse(response.getStatusCode(), servTran.getRequest());
                servTran.sendResponse(byeResp);
            } else if (calleeDialog.equals(dialog)) {
                ServerTransaction servTran = (ServerTransaction) callerDialog.getApplicationData();
                byeResp = msgFactory.createResponse(response.getStatusCode(), servTran.getRequest());
                servTran.sendResponse(byeResp);
            } else {

            }
            System.out.println("send bye response to peer:" + byeResp.toString());
        } catch (ParseException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (SipException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (InvalidArgumentException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    /* (non-Javadoc)
     * @see javax.sip.SipListener#processResponse(javax.sip.ResponseEvent)
     *
     */
    public void processResponse(ResponseEvent arg0) {
        log.info("=======>进入processResponse");
        // FIXME 需要判断各个响应对应的是什么请求
        Response response = arg0.getResponse();

        System.out.println("recv the response :" + response.toString());
        System.out.println("respone to request : " + arg0.getClientTransaction().getRequest());

        if (response.getStatusCode() == Response.TRYING) {
            System.out.println("The response is 100 response.");
            return;
        }

        try {
            ClientTransaction clientTran = (ClientTransaction) arg0.getClientTransaction();

            if (Request.INVITE.equalsIgnoreCase(clientTran.getRequest().getMethod())) {
                int statusCode = response.getStatusCode();
                Response callerResp = null;

                callerResp = msgFactory.createResponse(statusCode, serverTransactionId.getRequest());

                // 更新contact头域值，因为后面的消息是根据该URI来路由的
                ContactHeader contactHeader = headerFactory.createContactHeader();
                Address address = addressFactory.createAddress("sip:17714530605@" + ipAddr + ":" + port);
                contactHeader.setAddress(address);
                contactHeader.setExpires(3600);
                callerResp.addHeader(contactHeader);

                // 拷贝to头域
                ToHeader toHeader = (ToHeader) response.getHeader(ToHeader.NAME);
                callerResp.setHeader(toHeader);

                // 拷贝相应的消息体
                ContentLength contentLen = (ContentLength) response.getContentLength();
                if (contentLen != null && contentLen.getContentLength() != 0) {
                    ContentType contentType = (ContentType) response.getHeader(ContentType.NAME);
                    System.out.println("the sdp contenttype is " + contentType);

                    callerResp.setContentLength(contentLen);
                    //callerResp.addHeader(contentType);
                    callerResp.setContent(response.getContent(), contentType);
                } else {
                    System.out.println("sdp is null.");
                }
                if (serverTransactionId != null) {
                    callerDialog = serverTransactionId.getDialog();
                    calleeDialog = clientTran.getDialog();
                    serverTransactionId.sendResponse(callerResp);
                    System.out.println("callerDialog=" + callerDialog);
                    System.out.println("serverTransactionId.branch=" + serverTransactionId.getBranchId());
                } else {
                    System.out.println("serverTransactionId is null.");
                }

                System.out.println("send response to caller : " + callerResp.toString());
            } else if (Request.BYE.equalsIgnoreCase(clientTran.getRequest().getMethod())) {
                doByeResponse(response, arg0);
            } else if (Request.CANCEL.equalsIgnoreCase(clientTran.getRequest().getMethod())) {
                //doCancelResponse(response, arg0);
            } else {

            }


        } catch (ParseException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (SipException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (InvalidArgumentException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (Exception ex) {
            ex.printStackTrace();
        }

    }

    private void doCancelResponse(Response response, ResponseEvent responseEvent) {
        //FIXME  需要验证参数的有效性
        ServerTransaction servTran = (ServerTransaction) callerDialog.getApplicationData();
        Response cancelResp;
        try {
            cancelResp = msgFactory.createResponse(response.getStatusCode(), servTran.getRequest());
            servTran.sendResponse(cancelResp);
        } catch (ParseException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (SipException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (InvalidArgumentException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }

    public void processTimeout(TimeoutEvent arg0) {
        // TODO Auto-generated method stub
        System.out.println(" processTimeout " + arg0.toString());
    }

    public void processTransactionTerminated(TransactionTerminatedEvent arg0) {
        // TODO Auto-generated method stub
        System.out.println(" processTransactionTerminated " + arg0.getClientTransaction().getBranchId()
                + " " + arg0.getServerTransaction().getBranchId());
    }

    private static SipStack sipStack = null;

    private static AddressFactory addressFactory = null;

    private static MessageFactory msgFactory = null;

    private static HeaderFactory headerFactory = null;

    private static SipProvider sipProvider = null;

    public void init() {
        SipFactory sipFactory = null;

        sipFactory = SipFactory.getInstance();
        if (null == sipFactory) {
            System.out.println("init sipFactory is null.");
            return;
        }

        sipFactory.setPathName("gov.nist");
        Properties properties = new Properties();
        properties.setProperty("javax.sip.STACK_NAME", "sipphone");
        // You need 16 for logging traces. 32 for debug + traces.
        // Your code will limp at 32 but it is best for debugging.
        properties.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "32");
     /*   properties.setProperty("gov.nist.javax.sip.DEBUG_LOG",
                "sipphonedebug.txt");
        properties.setProperty("gov.nist.javax.sip.SERVER_LOG",
                "sipphonelog.txt");*/
        try {
            sipStack = sipFactory.createSipStack(properties);
        } catch (PeerUnavailableException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return;
        }

        try {
            headerFactory = sipFactory.createHeaderFactory();
            addressFactory = sipFactory.createAddressFactory();
            msgFactory = sipFactory.createMessageFactory();
            /*ListeningPoint lp = sipStack.createListeningPoint("0.0.0.0",
                    5060, "udp");
            SipPhone listener = this;

            sipProvider = sipStack.createSipProvider(lp);
            System.out.println("udp provider " + sipProvider.toString());
            sipProvider.addSipListener(listener);*/

            createListeningPoint("0.0.0.0", 5060, this);
        } catch (Exception ex) {
            ex.printStackTrace();
            return;
        }

    }

    private ListeningPoint tcp;

    private ListeningPoint udp;

    private void createListeningPoint(String ip, int port, SipPhone sipServer) throws InvalidArgumentException,
            TransportNotSupportedException,
            ObjectInUseException, TooManyListenersException {
        tcp = sipStack.createListeningPoint(ip, port, "tcp");
        udp = sipStack.createListeningPoint(ip, port, "udp");
        sipProvider = sipStack.createSipProvider(tcp);
        sipProvider.addSipListener(sipServer);
        sipProvider = sipStack.createSipProvider(udp);
        sipProvider.addSipListener(sipServer);
    }

}