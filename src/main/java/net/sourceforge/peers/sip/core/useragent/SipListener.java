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


package net.sourceforge.peers.sip.core.useragent;

import net.sourceforge.peers.sip.syntaxencoding.SipUriSyntaxException;
import net.sourceforge.peers.sip.transport.SipRequest;
import net.sourceforge.peers.sip.transport.SipResponse;

public interface SipListener {

    public void registering(SipRequest sipRequest);

    public void registerSuccessful(UserAgent userAgent, SipResponse sipResponse);

    public void registerFailed(UserAgent userAgent, SipResponse sipResponse);

    public void incomingCall(SipRequest sipRequest, SipResponse provResponse);

    public void remoteHangup(SipRequest sipRequest) throws SipUriSyntaxException;

    /**
     * 响铃
     * @param sipResponse
     * @param userAgent
     */
    public void ringing(SipResponse sipResponse, UserAgent userAgent);

    /**
     * 通话中
     * @param sipResponse
     * @param userAgent
     */
    public void calleePickup(SipResponse sipResponse, UserAgent userAgent);

    public void error(SipResponse sipResponse, UserAgent userAgent) throws SipUriSyntaxException;

}
