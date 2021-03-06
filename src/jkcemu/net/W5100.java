/*
 * (c) 2011-2020 Jens Mueller
 *
 * Kleincomputer-Emulator
 *
 * Emulation des TCP/IP-Ethernet-Controllers WIZnet W5100
 */

package jkcemu.net;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.BindException;
import java.net.DatagramPacket;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import javax.net.ServerSocketFactory;
import javax.net.SocketFactory;
import jkcemu.Main;
import jkcemu.base.EmuUtil;


public class W5100
{
  // Masken fuer Eigenschaft KCNet.SYSPROP_DEBUG
  private static final int DEBUG_MASK_MSG    = 0x10;
  private static final int DEBUG_MASK_STATUS = 0x20;
  private static final int DEBUG_MASK_READ   = 0x40;
  private static final int DEBUG_MASK_WRITE  = 0x80;

  // Adressen der Register
  private static final int ADDR_MR     = 0x0000;
  private static final int ADDR_GWR    = 0x0001;
  private static final int ADDR_SUBR   = 0x0005;
  private static final int ADDR_SHAR   = 0x0009;
  private static final int ADDR_SIPR   = 0x000F;
  private static final int ADDR_IR     = 0x0015;
  private static final int ADDR_RTR    = 0x0017;
  private static final int ADDR_RCR    = 0x0019;
  private static final int ADDR_RMSR   = 0x001A;
  private static final int ADDR_TMSR   = 0x001B;
  private static final int ADDR_PTIMER = 0x0028;


  public class SocketData
  {
    // Socket-Kommandos
    private static final int CMD_NONE      = 0x00;
    private static final int CMD_OPEN      = 0x01;
    private static final int CMD_LISTEN    = 0x02;
    private static final int CMD_CONNECT   = 0x04;
    private static final int CMD_DISCON    = 0x08;
    private static final int CMD_CLOSE     = 0x10;
    private static final int CMD_SEND      = 0x20;
    private static final int CMD_SEND_MAC  = 0x21;
    private static final int CMD_SEND_KEEP = 0x22;
    private static final int CMD_RECV      = 0x40;

    // Socket-Status
    private static final byte SOCK_CLOSED      = (byte) 0x00;
    private static final byte SOCK_INIT        = (byte) 0x13;
    private static final byte SOCK_LISTEN      = (byte) 0x14;
    private static final byte SOCK_ESTABLISHED = (byte) 0x17;
    private static final byte SOCK_UDP         = (byte) 0x22;
    private static final byte SOCK_CLOSING     = (byte) 0x1A;
    private static final byte SOCK_CLOSE_WAIT  = (byte) 0x1C;
    private static final byte SOCK_IPRAW       = (byte) 0x32;
    private static final byte SOCK_MACRAW      = (byte) 0x42;
    private static final byte SOCK_PPPOE       = (byte) 0x5F;

    // Offset der Socket-Register
    private static final int Sn_CR     = 0x01;
    private static final int Sn_IR     = 0x02;
    private static final int Sn_SR     = 0x03;
    private static final int Sn_PORT   = 0x04;
    private static final int Sn_DHAR   = 0x06;
    private static final int Sn_DIPR   = 0x0C;
    private static final int Sn_DPORT  = 0x10;
    private static final int Sn_PROTO  = 0x14;
    private static final int Sn_TTL    = 0x16;
    private static final int Sn_TX_FSR = 0x20;
    private static final int Sn_TX_RR  = 0x22;
    private static final int Sn_TX_WR  = 0x24;
    private static final int Sn_RX_RSR = 0x26;
    private static final int Sn_RX_RD  = 0x28;
    private static final int Sn_RX_WR  = 0x2A;

    // Interrupt-Bits
    private static final int INT_CON_MASK     = 0x01;
    private static final int INT_DISCON_MASK  = 0x02;
    private static final int INT_RECV_MASK    = 0x04;
    private static final int INT_TIMEOUT_MASK = 0x08;
    private static final int INT_SEND_OK_MASK = 0x10;


    private int               socketNum;
    private int               baseAddr;
    private int               lastStatus;
    private int               rxReadReg;
    private int               rxWriteReg;
    private int               txReadReg;
    private int               txWriteReg;
    private boolean           rxFilled;
    private boolean           nonIPv4MsgShown;
    private boolean           recvEnabled;
    private volatile boolean  threadsEnabled;
    private volatile boolean  cmdThreadNoWait;
    private byte[]            recvBuf;
    private byte[]            sendBuf;
    private Thread            cmdThread;
    private Thread            recvThread;
    private EmuDatagramSocket datagramSocket;
    private ServerSocket      serverSocket;
    private Socket            socket;
    private InputStream       recvStream;
    private OutputStream      sendStream;


    private SocketData( int socketNum, int baseAddr )
    {
      this.socketNum       = socketNum;
      this.baseAddr        = baseAddr;
      this.lastStatus      = SOCK_CLOSED;
      this.threadsEnabled  = true;
      this.cmdThreadNoWait = false;
      this.recvBuf         = null;
      this.sendBuf         = null;
      this.cmdThread       = null;
      this.recvThread      = null;
      this.datagramSocket  = null;
      this.serverSocket    = null;
      this.socket          = null;
      this.recvStream      = null;
      this.sendStream      = null;
      initialize();
    }


    private void checkShowNonIPv4Msg( InetAddress inetAddr )
    {
      if( !this.nonIPv4MsgShown ) {
	
	String ipAddrText = null;
	if( inetAddr != null ) {
	  ipAddrText = inetAddr.toString();
	}
	if( ipAddrText != null ) {
	  if( ipAddrText.isEmpty() ) {
	    ipAddrText = null;
	  }
	}
	StringBuilder buf = new StringBuilder( 512 );
	buf.append( "Es wurden Daten von" );
	if( ipAddrText != null ) {
	  buf.append( " der IP-Adresse " );
	  buf.append( ipAddrText );
	} else {
	  buf.append( " einer IP-Adresse" );
	}
	buf.append( " empfangen,\n"
		+ "deren Format von KCNet nicht unterst\u00FCtzt wird.\n" );
	if( inetAddr != null ) {
	  if( inetAddr instanceof Inet6Address ) {
	    buf.append( "Die Gegenstelle benutzt IPv6,"
		+ " KCNet beherrscht aber nur IPv4.\n" );
	  }
	}
	buf.append( "Aus diesem Grund kann JKCEMU die IP-Adresse nicht\n"
		+ "in das emulierte KCNet eintragen,\n"
		+ "wodurch das im Emulator laufende Nertwerkprogramm\n"
		+ "eine Gegenstelle ohne g\u00FCltige IP-Adresse sieht." );
	EmuUtil.fireShowInfoDlg( Main.getScreenFrm(), buf.toString() );
	this.nonIPv4MsgShown = true;
      }
    }


    private synchronized void closeSocket()
    {
      EmuDatagramSocket datagramSocket = this.datagramSocket;
      if( datagramSocket != null ) {
        datagramSocket.close();
      }
      EmuUtil.closeSilently( this.sendStream );
      EmuUtil.closeSilently( this.recvStream );
      EmuUtil.closeSilently( this.socket );
      EmuUtil.closeSilently( this.serverSocket );
      this.sendStream      = null;
      this.recvStream      = null;
      this.socket          = null;
      this.serverSocket    = null;
      this.datagramSocket  = null;
      this.rxReadReg       = 0;
      this.rxWriteReg      = 0;
      this.txReadReg       = 0;
      this.txWriteReg      = 0;
      this.rxFilled        = false;
      this.nonIPv4MsgShown = false;
      setSR( SOCK_CLOSED );
    }


    private EmuDatagramSocket createDatagramSocket( boolean forceCreation )
							throws IOException
    {
      EmuDatagramSocket ds   = null;
      boolean           mc   = ((getMemByte( this.baseAddr ) & 0x80) != 0);
      int               port = getMemWord(
				this.baseAddr + (mc ? Sn_DPORT : Sn_PORT) );
      /*
       * Wenn eine Portnummer bekannt ist, dann schauen,
       * ob diese schon reserviert ist und die Reservierung aufheben.
       */
      if( port != 0 ) {
	ds = fetchReservedDatagramSocket( port );
      }

      // Socket anlegen
      if( mc ) {
	if( ds != null ) {
	  /*
	   * Da MulticastSocket benoetigt wird,
	   * kann der reservierte DatagramSocket nicht verwendet werden.
	   * Deshalb diesen schliessen, um den Port freizugeben,
	   * und danach schnell den MulticastSocket an diesen Port binden.
	   */
	  ds.close();
	  ds = null;
	}
	if( port != 0 ) {
	  ds = EmuDatagramSocket.createMulticastSocket( port );
	} else {
	  if( forceCreation ) {
	    ds = EmuDatagramSocket.createMulticastSocket();
	  }
	}
	if( ds != null ) {
	  try {
	    ds.setTimeToLive( getMemByte( this.baseAddr + Sn_TTL ) );
	  }
	  catch( IllegalArgumentException ex ) {}
	  InetAddress iAddr = createInetAddrByMem( this.baseAddr + Sn_DIPR );
	  if( iAddr != null ) {
	    ds.joinGroup(
			new InetSocketAddress( iAddr, ds.getLocalPort() ),
			NetworkInterface.getByInetAddress( iAddr ) );
	  }
	}
      } else {
	if( ds == null ) {
	  if( port != 0 ) {
	    ds = EmuDatagramSocket.createDatagramSocket( port );
	  } else {
	    if( forceCreation ) {
	      ds = EmuDatagramSocket.createDatagramSocket();
	    }
	  }
	}
      }
      return ds;
    }


    private void die()
    {
      this.threadsEnabled = false;
      wakeUpThread( this.cmdThread, true );
      wakeUpThread( this.recvThread, true );
    }


    private void doSocketConnect()
    {
      if( getSR() == SOCK_INIT ) {
	boolean       done          = false;
	int           timeoutMillis = 0;
	SocketAddress socketAddr    = null;
	Exception     socketEx      = null;
	if( !isIpAddrConflict( this.baseAddr + Sn_DIPR ) ) {
	  Socket socket = null;
	  try {
	    socketAddr = new InetSocketAddress(
			createInetAddrByMem( this.baseAddr + Sn_DIPR ),
			getMemWord( this.baseAddr + Sn_DPORT ) );
	    timeoutMillis = getTimeoutMillis();
	    socket        = createSocket();
	    socket.connect( socketAddr, timeoutMillis );
	    int bufSize = getTxBufSize( this.socketNum );
	    if( bufSize > 0 ) {
	      this.sendStream = new BufferedOutputStream(
					socket.getOutputStream(),
					bufSize );
	    } else {
	      this.sendStream = new BufferedOutputStream(
					socket.getOutputStream() );
	    }
	    this.recvStream = socket.getInputStream();
	    this.socket     = socket;
	    synchronized( this ) {
	      setSR( SOCK_ESTABLISHED );
	      setSnIRBits( INT_CON_MASK );
	      this.recvEnabled = true;
	    }
	    fireRunRecvThread();
	    done = true;

	    // Debug-Meldung
	    if( (getDebugMask() & DEBUG_MASK_MSG) != 0 ) {
	      System.out.printf(
			"W5100 Socket %d: connected to %s\n",
			this.socketNum,
			socketAddr.toString() );
	    }
	  }
	  catch( Exception ex ) {
	    socketEx = ex;
	  }
	}
	if( !done ) {
	  if( (getDebugMask() & DEBUG_MASK_MSG) != 0 ) {
	    String s = null;
	    if( socketAddr != null ) {
	      s = socketAddr.toString();
	    }
	    if( s == null ) {
	      s = "";
	    }
	    System.out.printf(
			"connect to: %s, timeout=%dms\n",
			s.isEmpty() ? "?" : s,
			timeoutMillis );
	    if( socketEx != null ) {
	      socketEx.printStackTrace( System.out );
	    }
	  }
	  EmuUtil.closeSilently( socket );
	  closeSocket();
	  setSnIRBits( INT_TIMEOUT_MASK );
	}
      }
      setCR( CMD_NONE );
    }


    private void doSocketListen()
    {
      if( getSR() == SOCK_LISTEN ) {
	try {
	  ServerSocket serverSocket = this.serverSocket;
	  if( serverSocket == null ) {
	    serverSocket = createServerSocket(
				getMemWord( this.baseAddr + Sn_PORT ),
				1 );
	    this.serverSocket = serverSocket;
	    if( (serverSocket != null)
		&& ((getDebugMask() & DEBUG_MASK_MSG) != 0) )
	    {
	      System.out.printf(
		"W5100 Socket %d: tcp server socket bound at port %d\n",
		this.socketNum,
		serverSocket.getLocalPort() );
	    }
	  }
	  Socket socket   = serverSocket.accept();
	  this.sendStream = socket.getOutputStream();
	  this.recvStream = socket.getInputStream();
	  this.socket     = socket;
	  if( !setMemIpAddr(
			this.baseAddr + Sn_DIPR,
			socket.getInetAddress() ) )
	  {
	    checkShowNonIPv4Msg( socket.getInetAddress() );
	  }
	  setMemWord( this.baseAddr + Sn_DPORT, socket.getPort() );
	  synchronized( this ) {
	    setSR( SOCK_ESTABLISHED );
	    setSnIRBits( INT_CON_MASK );
	    this.recvEnabled = true;
	  }
	  fireRunRecvThread();
	}
	catch( Exception ex ) {
	  /*
	   * Beim realen W5100-Chip kann ein LISTEN nicht fehlschlagen.
	   * Aus diesem Grund wird hier kein Fehler signalisiert,
	   * sondern weiterhin der Zustand SOCK_LISTEN vorgegaukelt.
	   */
	  if( getCR() == CMD_LISTEN ) {
	    checkPermissionDenied( ex );
	    if( (getDebugMask() & DEBUG_MASK_MSG) != 0 ) {
	      ex.printStackTrace( System.out );
	    }
	  }
	  EmuUtil.closeSilently( socket );
	}
      }
      switch( getCR() ) {
	case CMD_CLOSE:
	case CMD_DISCON:
	  closeSocket();
	  break;
	case CMD_LISTEN:
	  setCR( CMD_NONE );
	  break;
      }
    }


    private byte[] getRecvBuf( int size )
    {
      byte[] buf = this.recvBuf;
      if( buf != null ) {
	if( buf.length < size ) {
	  buf = null;
	}
      }
      if( buf == null ) {
	buf = new byte[ size ];
	this.recvBuf = buf;
      }
      return buf;
    }


    private byte[] getSendBuf( int size )
    {
      byte[] buf = this.sendBuf;
      if( buf != null ) {
	if( buf.length < size ) {
	  buf = null;
	}
      }
      if( buf == null ) {
	buf = new byte[ size ];
	this.sendBuf = buf;
      }
      return buf;
    }


    private synchronized void fireRunCmdThread()
    {
      if( this.cmdThread != null ) {
	synchronized( this.cmdThread ) {
	  try {
	    this.cmdThread.notify();
	  }
	  catch( IllegalMonitorStateException ex ) {
	    this.cmdThreadNoWait = true;
	  }
	}
      } else {
	if( this.threadsEnabled ) {
	  Thread t = new Thread(
		Main.getThreadGroup(),
		new Runnable()
		{
		  @Override
		  public void run()
		  {
		    runCmdThread();
		  }
		},
	        String.format(
			"JKCEMU KCNET socket %d send",
			this.socketNum ) );
	  t.start();
	  Thread.State threadState = t.getState();
	  while( threadState == Thread.State.NEW ) {
	    try {
	      Thread.sleep( 10 );
	    }
	    catch( InterruptedException ex ) {}
	    threadState = t.getState();
	  }
	  this.cmdThread = t;
	}
      }
    }


    private synchronized void fireRunRecvThread()
    {
      if( this.recvThread == null ) {
	if( this.threadsEnabled ) {
	  this.recvThread = new Thread(
		Main.getThreadGroup(),
		new Runnable()
		{
		  @Override
		  public void run()
		  {
		    runRecvThread();
		  }
		},
	        String.format(
			"JKCEMU KCNET socket %d receive",
			this.socketNum ) );
	  this.recvThread.start();
	}
      } else {
	wakeUpThread( this.recvThread, false );
      }
    }


    private int getCR()
    {
      return getMemByte( this.baseAddr + Sn_CR );
    }


    private int getSR()
    {
      return getMemByte( this.baseAddr + Sn_SR );
    }


    private int getTimeoutMillis()
    {
      return getMemWord( ADDR_RTR ) * getMemByte( ADDR_RCR ) / 10;
    }


    private void initialize()
    {
      // Destination HW Adresse
      setMemByte( this.baseAddr + Sn_DHAR, 0xFF );
      setMemByte( this.baseAddr + Sn_DHAR + 1, 0xFF );
      setMemByte( this.baseAddr + Sn_DHAR + 2, 0xFF );
      setMemByte( this.baseAddr + Sn_DHAR + 3, 0xFF );
      setMemByte( this.baseAddr + Sn_DHAR + 4, 0xFF );
      setMemByte( this.baseAddr + Sn_DHAR + 5, 0xFF );

      // Time To Live
      setMemByte( this.baseAddr + Sn_TTL, 0x80 );

      // TX Free Size (16 Bit)
      setMemByte( this.baseAddr + Sn_TX_FSR, 0x08 );

      this.rxReadReg       = 0;
      this.rxWriteReg      = 0;
      this.txReadReg       = 0;
      this.txWriteReg      = 0;
      this.rxFilled        = false;
      this.recvEnabled     = false;
      this.cmdThreadNoWait = false;
      this.nonIPv4MsgShown = false;
    }


    private void logDatagramSocketBound()
    {
      EmuDatagramSocket ds = this.datagramSocket;
      if( ds != null ) {
	System.out.printf(
		"W5100 Socket %d: %s socket bound at port %d\n",
		this.socketNum,
		ds.isMulticastSocket() ? "multicast" : "datagram",
		ds.getLocalPort() );
      }
    }


    private int readMemByte( int addr )
    {
      /*
       * Laut Datenblatt muss bei 16-Bit-Registern immer das
       * hoeherwertige Byte zuerst gelesen werden.
       * Aus diesem Grund wird hier beim Zugriff auf das hoeherwertige Byte
       * der 16-Bit-Wert ermittelt und in den Speicher geschrieben,
       * aus dem dann auch gelesen wird.
       */
      switch( addr & 0x00FF ) {
	case Sn_TX_FSR:
	  synchronized( this ) {
	    int fsr     = 0;
	    int bufSize = getTxBufSize( this.socketNum );
	    if( bufSize > 0 ) {
	      int mask = bufSize - 1;
	      int wr   = this.txWriteReg & mask;
	      int rr   = this.txReadReg & mask;
	      if( wr == rr ) {
		fsr = bufSize;
	      } else {
		if( rr < wr ) {
		  rr += bufSize;
		}
		fsr = (rr - wr) & mask;
	      }
	    }
	    setMemWord( addr, fsr );
	  }
	  break;
	case Sn_TX_RR:
	  synchronized( this ) {
	    setMemWord( addr, this.txReadReg );
	  }
	  break;
	case Sn_TX_WR:
	  synchronized( this ) {
	    setMemWord( addr, this.txWriteReg );
	  }
	  break;
	case Sn_RX_RSR:
	  // Anzahl der Bytes im Empfangspuffer
	  synchronized( this ) {
	    int rsr = 0;
	    if( rxFilled ) {
	      int bufSize = getRxBufSize( this.socketNum );
	      if( bufSize > 0 ) {
		int mask = bufSize - 1;
		int wr   = this.rxWriteReg & mask;
		int rr   = this.rxReadReg & mask;
		if( wr == rr ) {
		  rsr = bufSize;
		} else {
		  if( wr < rr ) {
		    wr += bufSize;
		  }
		  rsr = (wr - rr) & mask;
		}
	      }
	    }
	    setMemWord( addr, rsr );
	  }
	  break;
	case Sn_RX_RD:
	  synchronized( this ) {
	    setMemWord( addr, this.rxReadReg );
	  }
	  break;
	case Sn_RX_WR:
	  synchronized( this ) {
	    setMemWord( addr, this.rxWriteReg );
	  }
	  break;
      }
      int rv = 0;
      if( ((getDebugMask() & DEBUG_MASK_STATUS) != 0)
	  && (addr == (this.baseAddr + Sn_SR)) )
      {
        synchronized( getLoggingLockObj() ) {
	  rv = getMemByte( addr );
	  if( rv != this.lastStatus ) {
	    this.lastStatus = rv;
	    System.out.printf(
		"W5100 read: status of socket %d has changed to %02X\n",
		this.socketNum,
		rv );
	  }
	}
      } else {
	rv = getMemByte( addr );
      }
      return rv;
    }


    private void receiveIPRAW()
    {
      boolean received = false;
      while( !received && (getSR() == SOCK_IPRAW) ) {
	int bufAddr = 0;
	int bufSize = 0;
	int rr      = 0;
	int wr      = 0;
	synchronized( this ) {
	  bufAddr = getRxBufAddr( this.socketNum );
	  bufSize = getRxBufSize( this.socketNum );
	  rr      = this.rxReadReg;
	  wr      = this.rxWriteReg;
	}
	if( (bufSize > 0)
	    && getMemByte( this.baseAddr + Sn_PROTO ) == 0x01 )
	{
	  /*
	   * ICMP Paket empfangen,
	   * Das kann in der Emulation nur eine simulierte Ping-Antwort sein.
	   * Dazu wird die Liste der gesendeten Pings durchgegangen.
	   * Pings mit einer Antwort (Echo, Timeout oder Fehler)
	   * werden in jedem Fall aus der Liste entfernt.
	   * Im Falle einer positiven Antwort oder eines Fehlers
	   * wird der Empfang eines entsprechendes
	   * ICMP Pakets simuliert.
	   */
	  Ping                 usedPing = null;
	  java.util.List<Ping> pings    = getPings();
	  synchronized( pings ) {
	    int idx = 0;
	    while( (usedPing == null) && (idx < pings.size()) ) {
	      Ping ping = pings.get( idx );
	      if( ping.checkError() ) {
		usedPing = ping;
		pings.remove( idx );
	      } else {
		Boolean status = ping.getReachable();
		if( status != null ) {
		  if( status.booleanValue() ) {
		    usedPing = ping;
		  }
		  pings.remove( idx );
		} else {
		  idx++;
		}
	      }
	    }
	  }
	  if( usedPing != null ) {
	    byte[] pkg = usedPing.getPackageData();
	    if( pkg != null ) {
	      int nFree = bufSize;
	      int mask  = bufSize - 1;
	      rr &= mask;
	      wr &= mask;
	      if( wr != rr ) {
		if( wr < rr ) {
		  wr += bufSize;
		}
		nFree = (wr - rr) & mask;
	      }
	      if( (pkg.length >= 4) && ((pkg.length + 6) <  nFree) ) {

		// W5100 IPRAW Header fuellen
		setMemIpAddr(
			bufAddr + (wr & mask),
			usedPing.getInetAddress() );
		wr += 4;
		setMemWord(
			bufAddr + (wr & mask),
			pkg.length );
		wr += 2;

		// ICMP Echo Reply Header
		pkg[ 0 ] = (byte) (usedPing.checkError() ? 3 : 0);
		pkg[ 1 ] = (byte) 0;
		pkg[ 2 ] = (byte) 0;
		pkg[ 3 ] = (byte) 0;
		long cks = computeChecksum( pkg, 0, pkg.length );
		pkg[ 2 ] = (byte) ((cks >> 8) & 0xFF);
		pkg[ 3 ] = (byte) (cks & 0xFF);
		for( int i = 0; i < pkg.length; i++ ) {
		  setMemByte( bufAddr + (wr & mask), pkg[ i ] );
		  wr++;
		}

		// Empfang signalisieren
		synchronized( this ) {
		  this.recvEnabled = false;
		  this.rxFilled    = true;
		  this.rxWriteReg  = wr & mask;
		  setSnIRBits( INT_RECV_MASK );
		}
		received = true;
	      }
	    }
	  }
	}
	if( !received ) {
	  try {
	    Thread.sleep( 10 );
	  }
	  catch( InterruptedException ex ) {}
	}
      }
    }


    private void receiveTCP()
    {
      int bufAddr = 0;
      int bufSize = 0;
      int rr      = 0;
      int wr      = 0;
      synchronized( this ) {
	bufAddr = getRxBufAddr( this.socketNum );
	bufSize = getRxBufSize( this.socketNum );
	rr      = this.rxReadReg;
	wr      = this.rxWriteReg;
      }
      if( bufSize > 0 ) {
	try {
	  InputStream in = this.recvStream;
	  if( in != null ) {
	    int mask = bufSize - 1;
	    rr &= mask;
	    wr &= mask;
	    if( !this.rxFilled || (wr != rr) ) {

	      // warten auf das erste Byte
	      int b = in.read();
	      if( b >= 0 ) {
		int nRead = 1;
		setMemByte( bufAddr + (wr & mask), b );
		wr = (wr + 1) & mask;

		// weitere Bytes empfangen?
		int nAvailable = in.available();
		while( (nAvailable > 0) && (wr != rr) ) {
		  b = in.read();
		  if( b < 0 ) {
		    break;
		  }
		  nRead++;
		  setMemByte( bufAddr + (wr & mask), b );
		  wr = (wr + 1) & mask;
		  --nAvailable;
		  if( nAvailable == 0 ) {
		    // zwischenzeitlich neue Bytes empfangen?
		    nAvailable = in.available();
		  }
		}
		synchronized( this ) {
		  this.recvEnabled = false;
		  this.rxFilled    = true;
		  this.rxWriteReg  = wr;
		  setSnIRBits( INT_RECV_MASK );
		}
		if( (nRead > 0)
		    && ((getDebugMask() & DEBUG_MASK_MSG) != 0) )
		{
		  System.out.printf(
			"W5100 Socket %d: %d bytes received\n",
			this.socketNum,
			nRead );
		}
	      }
	      if( b < 0 ) {
		if( (getDebugMask() & DEBUG_MASK_MSG) != 0 ) {
		  System.out.printf(
			"W5100 Socket %d: tcp connection closed"
				+ " by remote host\n",
			this.socketNum );
		}
		setCR( CMD_NONE );
		setSR( SOCK_CLOSE_WAIT );
		Socket socket = this.socket;
		if( socket != null ) {
		  EmuUtil.closeSilently( socket );
		  this.recvStream = null;
		  this.sendStream = null;
		  this.socket     = null;
		}
		setSR( SOCK_CLOSED );
		fireRunCmdThread();
	      }
	    }
	  }
	}
	catch( IOException ex ) {
	  EmuUtil.closeSilently( this.recvStream );
	  this.recvStream = null;
	}
      }
    }


    private void receiveUDP()
    {
      boolean           received = false;
      EmuDatagramSocket dSocket  = this.datagramSocket;
      if( dSocket != null ) {
	int bufAddr = 0;
	int bufSize = 0;
	int rr      = 0;
	int wr      = 0;
	synchronized( this ) {
	  bufAddr = getRxBufAddr( this.socketNum );
	  bufSize = getRxBufSize( this.socketNum );
	  rr      = this.rxReadReg;
	  wr      = this.rxWriteReg;
	}
	if( bufSize > 0 ) {
	  int nFree = bufSize;
	  int mask  = bufSize - 1;
	  rr &= mask;
	  wr &= mask;
	  if( wr != rr ) {
	    if( wr < rr ) {
	      wr += bufSize;
	    }
	    nFree = (wr - rr) & mask;
	  }

	  // 8 Bytes Platz fuer den W5100 UDP Header lassen
	  if( nFree > 8 ) {
	    try {
	      byte[]         recvBuf = getRecvBuf( bufSize );
	      DatagramPacket packet  = new DatagramPacket(
							recvBuf,
							nFree - 8 );
	      if( dSocket.receive( getW5100(), packet ) ) {
		int len = packet.getLength();
		if( len > 0 ) {
		  String msgAddon = "";
		  if( (len <= bufSize) && (len <= (nFree - 8)) ) {

		    // W5100 UP Header fuellen
		    if( !setMemIpAddr(
				bufAddr + (wr & mask),
				packet.getAddress() ) )
		    {
		      checkShowNonIPv4Msg( packet.getAddress() );
		    }
		    wr += 4;
		    setMemWord( bufAddr + (wr & mask), packet.getPort() );
		    wr += 2;
		    setMemWord( bufAddr + (wr & mask), len );
		    wr += 2;

		    // Daten kopieren
		    byte[] data = packet.getData();
		    if( data != null ) {
		      int pos = packet.getOffset();
		      for( int i = 0; i < len; i++ ) {
			int b = 0;
			if( (pos >= 0) && (pos < data.length) ) {
			  b = data[ pos ] & 0xFF;
			}
			setMemByte( bufAddr + (wr & mask), b );
			pos++;
			wr++;
		      }
		    }

		    // Empfang signalisieren
		    synchronized( this ) {
		      this.recvEnabled = false;
		      this.rxFilled    = true;
		      this.rxWriteReg  = wr & mask;
		      setSnIRBits( INT_RECV_MASK );
		    }
		    received = true;
		  } else {
		    msgAddon = " but ignored due limited buffer size";
		  }
		  if( (getDebugMask() & DEBUG_MASK_MSG) != 0 ) {
		    System.out.printf(
			"W5100 Socket %d: %d bytes received%s\n",
			this.socketNum,
			len,
			msgAddon );
		  }
		}
	      }
	    }
	    catch( Exception ex ) {}
	  }
	}
      }
      if( !received ) {
	try {
	  Thread.sleep( 10 );
	}
	catch( InterruptedException ex ) {}
      }
    }


    private void runCmdThread()
    {
      while( this.threadsEnabled ) {
	int sr = getSR();
	switch( getCR() ) {
	  case CMD_OPEN:
	    if( sr == SOCK_UDP ) {
	      if( this.datagramSocket == null ) {
		try {
		  this.datagramSocket = createDatagramSocket( false );
		  if( (getDebugMask() & DEBUG_MASK_STATUS) != 0 ) {
		    logDatagramSocketBound();
		  }
		}
		catch( IOException ex ) {
		  checkPermissionDenied( ex );
		  if( (getDebugMask() & DEBUG_MASK_MSG) != 0 ) {
		    ex.printStackTrace( System.out );
		  }
		  closeSocket();
		}
		if( this.datagramSocket != null ) {
		  this.recvEnabled = true;
		  fireRunRecvThread();
		}
	      }
	    }
	    break;
	  case CMD_LISTEN:
	    doSocketListen();
	    break;
	  case CMD_CONNECT:
	    doSocketConnect();
	    break;
	  case CMD_DISCON:
	    boolean connected = (this.socket != null);
	    closeSocket();
	    if( connected ) {
	      setSnIRBits( INT_DISCON_MASK );
	    }
	    break;
	  case CMD_CLOSE:
	    closeSocket();
	    break;
	  case CMD_SEND:
	    switch( sr ) {
	      case SOCK_IPRAW:
		sendIPRAW();
		break;
	      case SOCK_ESTABLISHED:
		sendTCP();
		break;
	      case SOCK_UDP:
		sendUDP();
		break;
	      default:
		/*
		 * Da bei den verbindungslosen Diensten laut Spezifikation
		 * die erfolgreiche Uebertragung eines konkreten Paketes
		 * nicht garantiert ist, wird hier einfach so getan,
		 * als wenn das Paket gesendet wurde,
		 * wohl wissend, dass dem nicht so ist.
		 */
		this.txReadReg = this.txWriteReg;
		setSnIRBits( INT_SEND_OK_MASK );
	    }
	    break;
	  case CMD_RECV:
	    if( (sr == SOCK_ESTABLISHED)
		|| (sr == SOCK_UDP)
		|| (sr == SOCK_IPRAW) )
	    {
	      this.recvEnabled = true;
	      fireRunRecvThread();
	    }
	    break;
	}
	setCR( CMD_NONE );
	if( getSR() == SOCK_CLOSED ) {
	  closeSocket();
	}

	// warten
	if( this.cmdThreadNoWait ) {
	  this.cmdThreadNoWait = false;
	} else {
	  Thread thread = this.cmdThread;
	  if( thread != null ) {
	    synchronized( thread ) {
	      try {
		thread.wait();
	      }
	      catch( IllegalMonitorStateException ex ) {}
	      catch( InterruptedException ex ) {}
	    }
	  }
	}
      }
      closeSocket();
    }


    private void runRecvThread()
    {
      while( this.threadsEnabled ) {
	if( this.recvEnabled ) {
	  switch( getSR() ) {
	    case SOCK_ESTABLISHED:
	      receiveTCP();
	      break;
	    case SOCK_UDP:
	      receiveUDP();
	      break;
	    case SOCK_IPRAW:
	      receiveIPRAW();
	      break;
	  }
	}

	// warten auf Empfangsfreigabe
	Thread thread = this.recvThread;
	if( thread != null ) {
	  synchronized( thread ) {
	    try {
	      thread.wait( 30 );
	    }
	    catch( IllegalMonitorStateException ex ) {}
	    catch( InterruptedException ex ) {}
	  }
	}
      }
    }


    private void sendIPRAW()
    {
      boolean done = false;

      /*
       * pruefen, ob es sich um einen ICMP Echo Request (Ping) handelt,
       * wenn ja, die entsprechende IP-Adresse auf Erreichbarkeit testen
       * und im Erfolgsfall ein ICMP Echo Reply simulieren
       */
      if( getMemByte( this.baseAddr + Sn_PROTO ) == 0x01 ) {

	// ICMP
	int bufAddr = 0;
	int bufSize = 0;
	int rr      = 0;
	int wr      = 0;
	synchronized( this ) {
	  bufAddr = getTxBufAddr( this.socketNum );
	  bufSize = getTxBufSize( this.socketNum );
	  rr      = this.txReadReg;
	  wr      = this.txWriteReg;
	}
	int mask = bufSize - 1;
	if( (bufSize > 0)
	    && getMemByte( bufAddr + (this.txReadReg & mask) ) == 0x08 )
	{
	  // ICMP Echo Request
	  rr &= mask;
	  wr &= mask;
	  int packageSize = wr - rr;
	  if( packageSize < 0 ) {
	    packageSize = (wr + bufSize) - rr;
	  }
	  if( packageSize >= 4 ) {
	    InetAddress srcInetAddr = createInetAddrByMem( ADDR_SIPR );
	    InetAddress dstInetAddr = createInetAddrByMem(
					this.baseAddr + Sn_DIPR );
	    if( (srcInetAddr != null) && (dstInetAddr != null) ) {
	      byte[] packageData = null;
	      if( !dstInetAddr.equals( srcInetAddr )
		  || ((getMemByte( ADDR_MR ) & 0x10) == 0) )
	      {
		/*
		 * Wenn ein Echo Request an die eigene Adresse gesendet wird,
		 * und gleichzeitig Echo Reply deaktiviert ist,
		 * soll nichts empfangen werden.
		 * Dazu wird das Senden einfach unterbunden,
		 * aber trotzdem als erfolgreich zurueckgemeldet.
		 */
		packageData = new byte[ packageSize ];
		for( int i = 0; i < packageData.length; i++ ) {
		  packageData[ i ] = (byte) getMemByte(
				bufAddr + ((rr + i) & mask) );
		}
	      }

	      // Daten als gesendet markieren
	      this.txReadReg = wr;
	      setSnIRBits( INT_SEND_OK_MASK );
	      done = true;

	      // Ping starten
	      if( packageData != null ) {
		Ping ping = new Ping( dstInetAddr, packageData );
		ping.start();
		addPing( ping );
	      }
	    }
	  }
	}
      }
      if( !done ) {
	/*
	 * Daten konnten nicht gesendet werden
	 * (z.B. in der Emulation nicht unterstuetzt) -> Fehler melden
	 */
	synchronized( this ) {
	  this.txReadReg = this.txWriteReg;
	  setSnIRBits( INT_TIMEOUT_MASK );
	}
      }
    }


    private void sendTCP()
    {
      try {
	boolean      done = false;
	OutputStream out  = this.sendStream;
	if( out != null ) {
	  int bufSize = 0;
	  int bufAddr = 0;
	  int rr      = 0;
	  int wr      = 0;
	  synchronized( this ) {
	    bufSize = getTxBufSize( this.socketNum );
	    bufAddr = getTxBufAddr( this.socketNum );
	    rr      = this.txReadReg;
	    wr      = this.txWriteReg;
	  }
	  if( bufSize > 0 ) {
	    int nWritten = 0;
	    int mask     = bufSize - 1;
	    rr &= mask;
	    wr &= mask;
	    do {
	      out.write( getMemByte( bufAddr + rr ) );
	      nWritten++;
	      rr = (rr + 1) & mask;
	    } while( rr != wr );
	    out.flush();

	    // Daten als gesendet markieren
	    this.txReadReg = wr;
	    setSnIRBits( INT_SEND_OK_MASK );
	    done = true;

	    // Debug-Meldung
	    if( (nWritten > 0)
		&& ((getDebugMask() & DEBUG_MASK_MSG) != 0) )
	    {
	      System.out.printf(
			"W5100 Socket %d: %d bytes sent\n",
			this.socketNum,
			nWritten );
	    }
	  }
	}
	if( !done ) {
	  synchronized( this ) {
	    this.txReadReg = this.txWriteReg;
	    setSnIRBits( INT_TIMEOUT_MASK );
	  }
	}
      }
      catch( IOException ex ) {
	if( (getDebugMask() & DEBUG_MASK_MSG) != 0 ) {
	  ex.printStackTrace( System.out );
	}
	setCR( CMD_NONE );
	setSR( SOCK_CLOSE_WAIT );
	Socket socket = this.socket;
	if( socket != null ) {
	  EmuUtil.closeSilently( socket );
	  this.recvStream = null;
	  this.sendStream = null;
	  this.socket     = null;
	}
	setSR( SOCK_CLOSED );
	fireRunCmdThread();
      }
    }


    private void sendUDP()
    {
      boolean done    = false;
      int     bufSize = 0;
      int     bufAddr = 0;
      int     rr      = 0;
      int     wr      = 0;
      synchronized( this ) {
	bufSize = getTxBufSize( this.socketNum );
	bufAddr = getTxBufAddr( this.socketNum );
	rr      = this.txReadReg;
	wr      = this.txWriteReg;
      }
      if( bufSize > 0 ) {
	int len  = bufSize;
	int mask = bufSize - 1;
	rr &= mask;
	wr &= mask;
	if( wr != rr ) {
	  len = wr - rr;
	  if( len < 0 ) {
	    len += bufSize;
	  }
	}
	InetAddress srcInetAddr = createInetAddrByMem( ADDR_SIPR );
	InetAddress dstInetAddr = createInetAddrByMem(
					this.baseAddr + Sn_DIPR );
	if( (len > 0) && (srcInetAddr != null) && (dstInetAddr != null) ) {
	  try {
	    EmuDatagramSocket dSocket = this.datagramSocket;
	    if( dSocket == null ) {
	      dSocket             = createDatagramSocket( true );
	      this.datagramSocket = dSocket;
	      if( (getDebugMask() & DEBUG_MASK_STATUS) != 0 ) {
		logDatagramSocketBound();
	      }
	      this.recvEnabled = true;
	      fireRunRecvThread();
	    }
	    if( !isIpAddrConflict( this.baseAddr + Sn_DIPR ) ) {
	      byte[] sendBuf = getSendBuf( bufSize );
	      for( int i = 0; i < len; i++ ) {
		sendBuf[ i ] = (byte) getMemByte(
					bufAddr + ((rr + i) & mask) );
	      }
	      int            dstPort = getMemWord( this.baseAddr + Sn_DPORT );
	      DatagramPacket packet  = new DatagramPacket(
							sendBuf,
							len,
							dstInetAddr,
							dstPort );
	      dSocket.send( getW5100(), packet );

	      // Daten als gesendet markieren
	      this.txReadReg = wr;
	      setSnIRBits( INT_SEND_OK_MASK );
	      done = true;

	      // Debug-Meldung
	      if( (len > 0)
		  && ((getDebugMask() & DEBUG_MASK_MSG) != 0) )
	      {
		System.out.printf(
			"W5100 Socket %d: %d bytes sent to %s:%d\n",
			this.socketNum,
			len,
			dstInetAddr.toString(),
			dstPort );
	      }
	    }
	  }
	  catch( IOException ex ) {
	    checkPermissionDenied( ex );
	    if( (getDebugMask() & DEBUG_MASK_MSG) != 0 ) {
	      ex.printStackTrace( System.out );
	    }
	  }
	}
      }
      if( !done ) {
	synchronized( this ) {
	  this.txReadReg = this.txWriteReg;
	  setSnIRBits( INT_TIMEOUT_MASK );
	}
      }
    }


    private void setSnIRBits( int value )
    {
      setSnIRValue( getMemByte( this.baseAddr + Sn_IR ) | value );
    }


    private void setSnIRValue( int value )
    {
      value &= 0xFF;
      setMemByte( this.baseAddr + Sn_IR, value );
      int irValue = getMemByte( ADDR_IR );
      if( value == 0 ) {
	switch( this.socketNum ) {
	  case 0:
	    irValue &= 0xFE;		// Bit 0 zuruecksetzen
	    break;
	  case 1:
	    irValue &= 0xFD;		// Bit 1 zuruecksetzen
	    break;
	  case 2:
	    irValue &= 0xFB;		// Bit 2 zuruecksetzen
	    break;
	  case 3:
	    irValue &= 0xF7;		// Bit 3 zuruecksetzen
	    break;
	}
      } else {
	switch( this.socketNum ) {
	  case 0:
	    irValue |= 0x01;		// Bit 0 setzen
	    break;
	  case 1:
	    irValue |= 0x02;		// Bit 1 setzen
	    break;
	  case 2:
	    irValue |= 0x04;		// Bit 2 setzen
	    break;
	  case 3:
	    irValue |= 0x08;		// Bit 3 setzen
	    break;
	}
      }
      setMemByte( ADDR_IR, irValue );
    }


    private void setCR( int value )
    {
      setMemByte( this.baseAddr + Sn_CR, value );
    }


    private void setSR( int value )
    {
      if( (getDebugMask() & DEBUG_MASK_STATUS) != 0 ) {
        synchronized( getLoggingLockObj() ) {
	  int addr     = this.baseAddr + Sn_SR;
	  int oldValue = getMemByte( addr );
	  setMemByte( addr, value );
	  if( value != oldValue ) {
	    String text = null;
	    switch( value ) {
	      case SOCK_CLOSED:
		text = "CLOSED";
		break;
	      case SOCK_INIT:
		text = "INIT";
		break;
	      case SOCK_LISTEN:
		text = "LISTEN";
		break;
	      case SOCK_ESTABLISHED:
		text = "ESTABLISHED";
		break;
	      case SOCK_UDP:
		text = "UDP";
		break;
	      case SOCK_CLOSING:
		text = "CLOSING";
		break;
	      case SOCK_CLOSE_WAIT:
		text = "CLOSE_WAIT";
		break;
	      case SOCK_IPRAW:
		text = "IPRAW";
		break;
	      case SOCK_MACRAW:
		text = "MACRAW (not emulated -> socket closed)";
		break;
	      case SOCK_PPPOE:
		text = "PPPOE (not emulated -> socket closed)";
		break;
	    }
	    if( text != null ) {
	      System.out.printf(
			"W5100 Socket %d: status=%s\n",
			this.socketNum,
			text );
	    } else {
	      System.out.printf(
			"W5100 Socket %d: status=%02X\n",
			this.socketNum,
			text,
			value );
	    }
	  }
	}
      } else {
	setMemByte( this.baseAddr + Sn_SR, value );
      }
    }


    private void writeCommand( int addr, int value )
    {
      if( (getDebugMask() & DEBUG_MASK_MSG) != 0 ) {
	String text = null;
	switch( value ) {
	  case 0x01:
	    text = "OPEN";
	    break;
	  case 0x02:
	    text = "LISTEN";
	    break;
	  case 0x04:
	    text = "CONNECT";
	    break;
	  case 0x08:
	    text = "DISCON";
	    break;
	  case 0x10:
	    text = "CLOSE";
	    break;
	  case 0x20:
	    text = "SEND";
	    break;
	  case 0x21:
	    text = "SEND_MAC";
	    break;
	  case 0x40:
	    text = "RECV";
	    break;
	}
	if( text != null ) {
	  System.out.printf(
			"W5100 Socket %d: command=%s\n",
			this.socketNum,
			text );
	} else {
	  System.out.printf(
			"W5100 Socket %d: command=%02X\n",
			this.socketNum,
			value );
	}
      }
      switch( value ) {
	case CMD_OPEN:
	  if( getSR() == SOCK_CLOSED ) {
	    synchronized( this ) {
	      int bufSize = getRxBufSize( this.socketNum );
	      if( bufSize > 0 ) {
		int mask = bufSize - 1;
		this.rxReadReg &= mask;
		this.rxWriteReg &= mask;
	      } else {
		this.rxReadReg  = 0;
		this.rxWriteReg = 0;
	      }
	      bufSize = getTxBufSize( this.socketNum );
	      if( bufSize > 0 ) {
		int mask = bufSize - 1;
		this.txReadReg &= mask;
		this.txWriteReg &= mask;
	      } else {
		this.txReadReg  = 0;
		this.txWriteReg = 0;
	      }
	    }
	    switch( getMemByte( this.baseAddr ) & 0x0F ) {
	      case 0x01:
		setSR( SOCK_INIT );
		setCR( CMD_NONE );
		break;
	      case 0x02:
		setSR( SOCK_UDP );
		setMemByte( addr, value );
		// Kommando wird im runThread fortgesetzt.
		fireRunCmdThread();
		break;
	      case 0x03:
		setSR( SOCK_IPRAW );
		setCR( CMD_NONE );
		this.recvEnabled = true;
		fireRunRecvThread();	// Empfang ermoeglichen
		break;
	      default:
		/*
		 * In allen anderen Faellen, also auch bei den
		 * nicht emulierten Modi PPPoE und MACRAW,
		 * Socket einfach geschlossen lassen
		 */
		setCR( CMD_NONE );
	    }
	  } else {
	    setSR( SOCK_CLOSED );
	    setCR( CMD_NONE );
	  }
	  break;
	case CMD_LISTEN:
	  /*
	   * Kommando wird im runThread fortgesetzt.
	   * Der Status muss aber sofort auf SOCK_LISTEN gehen.
	   */
	  setMemByte( addr, value );
	  setSR( SOCK_LISTEN );
	  fireRunCmdThread();
	  break;
	case CMD_CONNECT:
	case CMD_SEND:
	case CMD_RECV:
	  // Diese Kommandos werden im cmdThread ausgefuehrt.
	  setMemByte( addr, value );
	  fireRunCmdThread();
	  break;
	case CMD_DISCON:
	case CMD_CLOSE:
	  setMemByte( addr, value );
	  if( (getSR() == SOCK_CLOSE_WAIT) && (this.socket != null) ) {
	    setSR( SOCK_CLOSING );
	  }
	  if( this.cmdThread != null ) {
	    /*
	     * ServerSocket schliessen, falls der Kommando-Thread
	     * in der accept-Methode haengt
	     */
	    ServerSocket serverSocket = this.serverSocket;
	    if( serverSocket != null ) {
	      try {
		serverSocket.close();
	      }
	      catch( IOException ex ) {}
	    }
	    // Diese Kommandos werden im cmdThread ausgefuehrt.
	    wakeUpThread( this.cmdThread, false );
	  } else {
	    closeSocket();
	  }
	  // uebriggebliebene reservierte DatagramSockets freigeben
	  releaseReservedDatagramSockets();
	  break;
	case CMD_SEND_MAC:
	  if( getSR() == SOCK_UDP ) {
	    // einfach so tun als sei es erfolgreich
	    this.txReadReg = this.txWriteReg;
	    setSnIRBits( INT_SEND_OK_MASK );
	  }
	  setMemByte( addr, 0 );
	  break;
	case CMD_SEND_KEEP:
	  if( (this.socket == null)
	      || (this.recvStream == null)
	      || (this.sendStream == null) )
	  {
	    setSnIRBits( INT_TIMEOUT_MASK );
	  }
	  setMemByte( addr, 0 );
	  break;
	default:
	  // Bei unbekannten Kommandos nichts tun.
	  setMemByte( addr, 0 );
	  break;
      }
    }


    private void writeMemByte( int addr, int value )
    {
      /*
       * Laut Datenblatt muss bei 16-Bit-Registern immer das
       * hoeherwertige Byte zuerst geschrieben werden.
       * Aus diesem Grund wird hier erst beim Zugriff auf das
       * niederwertige Byte der 16-Bit-Wert aus dem Speicher gelesen
       * und intern uebernommen.
       */
      switch( addr & 0x00FF ) {
	case Sn_SR:
	case Sn_TX_FSR:
	case Sn_TX_FSR + 1:
	case Sn_TX_RR:
	case Sn_TX_RR + 1:
	case Sn_RX_RSR:
	case Sn_RX_RSR + 1:
	case Sn_RX_WR:
	case Sn_RX_WR + 1:
	  // Register nicht beschreibbar
	  break;
	case Sn_IR:
	  setSnIRValue( getMemByte( addr ) & ~value );
	  break;
	case Sn_CR:
	  writeCommand( addr, value );
	  break;
	case Sn_TX_WR + 1:
	  setMemByte( addr, value );
	  synchronized( this ) {
	    this.txWriteReg = getMemWord( addr - 1 );
	  }
	  break;
	case Sn_RX_RD + 1:
	  setMemByte( addr, value );
	  synchronized( this ) {
	    int mask = getRxBufSize( this.socketNum ) - 1;
	    this.rxReadReg = getMemWord( addr - 1 ) & mask;
	    if( (this.rxReadReg & mask) == (this.rxWriteReg & mask) ) {
	      this.rxFilled = false;
	    }
	  }
	  break;

	default:
	  setMemByte( addr, value );
      }
    }
  };


  private byte[]                            localIpAddr;
  private byte[]                            mem;
  private Object                            loggingLockObj;
  private SocketData[]                      sockets;
  private java.util.List<Ping>              pings;
  private java.util.List<EmuDatagramSocket> reservedDatagramSockets;
  private ServerSocketFactory               serverSocketFactory;
  private SocketFactory                     socketFactory;
  private DhcpServer                        dhcpServer;
  private NetConfig                         netConfig;
  private int                               debugMask;


  public W5100()
  {
    this.localIpAddr = null;
    this.mem         = new byte[ 0x8000 ];
    Arrays.fill( this.mem, (byte) 0 );

    this.sockets = new SocketData[ 4 ];
    for( int i = 0; i < 4; i++ ) {
      this.sockets[ i ] = new SocketData( i, 0x0400 + (i * 0x0100) );
    }

    this.loggingLockObj          = new Object();
    this.serverSocketFactory     = ServerSocketFactory.getDefault();
    this.socketFactory           = SocketFactory.getDefault();
    this.dhcpServer              = new DhcpServer( this );
    this.reservedDatagramSockets = new ArrayList<>();
    this.pings                   = new ArrayList<>();
    this.netConfig               = null;
    this.debugMask               = 0;

    String text = System.getProperty( KCNet.SYSPROP_DEBUG );
    if( text != null ) {
      try {
	this.debugMask = Integer.parseInt( text );
      }
      catch( NumberFormatException ex ) {}
    }
  }


  private void addPing( Ping ping )
  {
    synchronized( this.pings ) {
      this.pings.add( ping );
    }
  }


  public void die()
  {
    for( SocketData socket : this.sockets ) {
      socket.die();
    }
  }


  public DhcpServer getDhcpServer()
  {
    return this.dhcpServer;
  }


  public synchronized NetConfig getNetConfig()
  {
    return this.netConfig;
  }


  public int readMemByte( int addr )
  {
    int rv = 0;
    if( (getDebugMask() & DEBUG_MASK_READ) != 0 ) {
      synchronized( getLoggingLockObj() ) {
	rv = readMemByteInternal( addr );
	System.out.printf(
		"W5100: read: addr=%04X value=%02X\n",
		addr,
		rv );
      }
    } else {
      rv = readMemByteInternal( addr );
    }
    return rv;
  }


  public int reservePort()
  {
    int port = -1;
    synchronized( this.reservedDatagramSockets ) {
      try {
	EmuDatagramSocket ds = EmuDatagramSocket.createDatagramSocket();
	port = ds.getLocalPort();
	this.reservedDatagramSockets.add( ds );
      }
      catch( Exception ex ) {}
    }
    return port;
  }


  public void reset( boolean powerOn )
  {
    // Sockets schliessen
    for( int i = 0; i < this.sockets.length; i++ ) {
      this.sockets[ i ].closeSocket();
    }

    // Threads aufwachen, falls sie blockiert sind
    wakeUpThreads();

    // Threads nochnals aufwachen, um ggf. Socket-Objekte zu schliessen
    wakeUpThreads();

    if( powerOn ) {
      Arrays.fill( this.mem, (byte) 0 );

      synchronized( this ) {
	this.netConfig = NetConfig.readNetConfig();
      }
      if( netConfig != null ) {
	byte[] hwAddr = netConfig.getHardwareAddr();
	if( hwAddr != null ) {
	  if( hwAddr.length == 6 ) {
	    int addr = ADDR_SHAR;
	    for( byte b : hwAddr ) {
	      this.mem[ addr++ ] = b;
	    }
	  }
	}
	byte[] gatewayIpAddr = netConfig.getManualIpAddr();
	if( gatewayIpAddr != null ) {
	  if( gatewayIpAddr.length == 4 ) {
	    int addr = ADDR_GWR;
	    for( byte b : gatewayIpAddr ) {
	      this.mem[ addr++ ] = b;
	    }
	  }
	}
	this.localIpAddr = netConfig.getIpAddr();
	byte[] ipAddr    = this.localIpAddr;
	if( ipAddr == null ) {
	  ipAddr = new byte[] { (byte) 127, (byte) 0, (byte) 0, (byte) 1 };
	}
	byte[] subnetMask = netConfig.getSubnetMask();
	if( !KCNet.getAutoConfig() ) {
	  ipAddr     = netConfig.getManualIpAddr();
	  subnetMask = netConfig.getManualSubnetMask();
	}
	if( ipAddr != null ) {
	  if( ipAddr.length == 4 ) {
	    int addr = ADDR_SIPR;
	    for( byte b : ipAddr ) {
	      this.mem[ addr++ ] = b;
	    }
	  }
	}
	if( subnetMask != null ) {
	  if( subnetMask.length == 4 ) {
	    int addr = ADDR_SUBR;
	    for( byte b : subnetMask ) {
	      this.mem[ addr++ ] = b;
	    }
	  }
	}
      }
    } else {
      Arrays.fill( this.mem, 0x0013, this.mem.length, (byte) 0 );
    }

    // Default-Werte
    this.mem[ ADDR_RTR ]     = (byte) 0x07;	// Retry Time-value (16 Bit)
    this.mem[ ADDR_RTR + 1 ] = (byte) 0xD0;
    this.mem[ ADDR_RCR ]     = (byte) 0x08;	// Retry Count
    this.mem[ ADDR_RMSR ]    = (byte) 0x55;	// RX Memory Size
    this.mem[ ADDR_TMSR ]    = (byte) 0x55;	// TX Memory Size
    this.mem[ ADDR_PTIMER ]  = (byte) 0x28;

    // Sockets
    for( int i = 0; i < this.sockets.length; i++ ) {
      this.sockets[ i ].initialize();
    }
  }


  public void writeMemByte( int addr, int value )
  {
    addr  &= 0xFFFF;
    value &= 0xFF;
    if( (getDebugMask() & DEBUG_MASK_WRITE) != 0 ) {
      System.out.printf(
		"W5100: write: addr=%04X value=%02X\n",
		addr,
		value );
    }
    if( (addr == ADDR_MR) && ((value & 0x80) != 0) ) {
      reset( true );
    }
    if( (addr >= 0) && (addr < this.mem.length) ) {
      if( addr == 0x0000 ) {
	this.mem[ addr ] = (byte) (value & 0x74);
      } else if( addr == ADDR_IR ) {
	/*
	 * Bits werden im Interrupt Register zurueckgesetzt,
	 * wenn diese Bits im uebergebenen Wert gesetzt sind.
	 */
	this.mem[ addr ] &= ~value;
      } else if( (addr >= 0x0400) && (addr < 0x0800) ) {
	this.sockets[ (addr >> 8) & 0x03 ].writeMemByte( addr, value );
      } else if( (addr < ADDR_SHAR) || (addr > (ADDR_SHAR + 5)) ) {
	this.mem[ addr ] = (byte) value;
      }
    }
  }


	/* --- private Methoden --- */

  private static void checkPermissionDenied( Exception ex )
  {
    if( ex != null ) {
      if( ex instanceof BindException ) {
	String msg = ex.getMessage();
	if( msg != null ) {
	  msg = msg.toLowerCase();
	  if( (msg.indexOf( "permission" ) >= 0)
	      || (msg.indexOf( "recht" ) >= 0) )
	  {
	    EmuUtil.fireShowErrorDlg(
		Main.getScreenFrm(),
		"Das Betriebssystem, auf dem JKCEMU l\u00E4uft,\n"
			+ "gestattet Ihnen die gew\u00FCnschte"
			+ " Netzwerkoperation nicht.",
		ex );
	  }
	}
      }
    }
  }


  private static int computeChecksum( byte[] data, int pos, int len )
  {
    long cks = 0;
    while( (pos < data.length) && (len > 0) ) {
      int w = ((int) data[ pos++ ] << 8) & 0xFF00;
      --len;
      if( (pos < data.length) && (len > 0) ) {
	w |= (((int) data[ pos++ ]) & 0xFF);
	--len;
      }
      cks += w;
    }
    return (int) (-1 - (cks + (cks >> 16)));
  }


  private InetAddress createInetAddrByMem( int addr )
  {
    InetAddress inetAddr = null;
    if( (addr + 3) < this.mem.length ) {
      try {
	byte[] ipAddr = new byte[ 4 ];
	for( int i = 0; i < ipAddr.length; i++ ) {
	  ipAddr[ i ] = this.mem[ addr++ ];
	}
	inetAddr = InetAddress.getByAddress( ipAddr );
      }
      catch( Exception ex ) {
	if( (getDebugMask() & DEBUG_MASK_MSG) != 0 ) {
	  ex.printStackTrace( System.out );
	}
      }
    }
    return inetAddr;
  }


  private Socket createSocket() throws IOException
  {
    Socket socket = null;
    if( this.socketFactory != null ) {
      socket = this.socketFactory.createSocket();
    } else {
      socket = new Socket();
    }
    return socket;
  }


  private ServerSocket createServerSocket(
				int port,
				int backlog) throws IOException
  {
    ServerSocket serverSocket = null;
    if( this.serverSocketFactory != null ) {
      serverSocket = this.serverSocketFactory.createServerSocket(
							port,
							backlog );
    } else {
      serverSocket = new ServerSocket( port, backlog );
    }
    return serverSocket;
  }


  private EmuDatagramSocket fetchReservedDatagramSocket( int port )
  {
    EmuDatagramSocket ds = null;
    synchronized( this.reservedDatagramSockets ) {
      int len = this.reservedDatagramSockets.size();
      int idx = 0;
      while( idx < len ) {
	EmuDatagramSocket tmpDS = this.reservedDatagramSockets.get( idx );
	if( tmpDS.getLocalPort() == port ) {
	  ds = tmpDS;
	  break;
	}
	idx++;
      }
    }
    return ds;
  }


  private int getBufSize( int socketNum, int addr )
  {
    int rv = 0;
    int t  = 0;
    int m  = getMemByte( addr );
    do {
      switch( m & 0x03 ) {
	case 0:
	  rv = 0x0400;
	  break;
	case 1:
	  rv = 0x0800;
	  break;
	case 2:
	  rv = 0x1000;
	  break;
	case 3:
	  rv = 0x2000;
	  break;
      }
      t += rv;
      m >>= 2;
      --socketNum;
    } while( socketNum >= 0 );
    if( t > 0x2000 ) {
      rv = 0;
    }
    return rv;
  }


  private int getDebugMask()
  {
    return this.debugMask;
  }


  private Object getLoggingLockObj()
  {
    return this.loggingLockObj;
  }


  private int getMemByte( int addr )
  {
    int rv = 0;
    if( (addr >= 0) && (addr < this.mem.length) ) {
      rv = (int) this.mem[ addr ] & 0xFF;
    }
    return rv;
  }


  private int getMemWord( int addr )
  {
    int rv = 0;
    if( (addr >= 0) && ((addr + 1) < this.mem.length) ) {
      rv = (((int) this.mem[ addr ] << 8) & 0xFF00)
			| (((int) this.mem[ addr + 1 ]) & 0x00FF);
    }
    return rv;
  }


  private java.util.List<Ping> getPings()
  {
    return this.pings;
  }


  private int getRxBufAddr( int socketNum )
  {
    int rv = 0x6000;
    int m  = getMemByte( ADDR_RMSR );
    while( socketNum > 0 ) {
      switch( m & 0x03 ) {
	case 0:
	  rv += 0x0400;
	  break;
	case 1:
	  rv += 0x0800;
	  break;
	case 2:
	  rv += 0x1000;
	  break;
	case 3:
	  rv += 0x2000;
	  break;
      }
      m >>= 2;
      --socketNum;
    }
    return rv;
  }


  private int getRxBufSize( int socketNum )
  {
    return getBufSize( socketNum, ADDR_RMSR );
  }


  private int getTxBufAddr( int socketNum )
  {
    int rv = 0x4000;
    int m  = getMemByte( ADDR_TMSR );
    while( socketNum > 0 ) {
      switch( m & 0x03 ) {
	case 0:
	  rv += 0x0400;
	  break;
	case 1:
	  rv += 0x0800;
	  break;
	case 2:
	  rv += 0x1000;
	  break;
	case 3:
	  rv += 0x2000;
	  break;
      }
      m >>= 2;
      --socketNum;
    }
    return rv;
  }


  private int getTxBufSize( int socketNum )
  {
    return getBufSize( socketNum, ADDR_TMSR );
  }


  /*
   * Die Methode dient zur Ermittlung der Referenz auf W5100
   * innerhalb von eingeschlossenen Klassen.
   */
  private W5100 getW5100()
  {
    return this;
  }


  private synchronized boolean isIpAddrConflict( int dstAddrIdx )
  {
    /*
     * Netzwerkkonfigurationsprogramme pruefen auf einen IP-Adresskonflikt,
     * indem sie vor dem Setzen der IP-Adresse ein Datenpaket
     * an genau diese schicken.
     * Wenn im lokalen Netz keine Netzwerkkarte diese IP-Adresse hat,
     * erfolgt keine ARP-Antwort und es tritt ein Timeout auf.
     */
    boolean rv = false;
    if( this.localIpAddr != null ) {
      rv = (!EmuUtil.equalsRegion(
			this.localIpAddr, 0,
			this.mem, ADDR_SIPR,
			4 )
	    && EmuUtil.equalsRegion(
			this.localIpAddr, 0,
			this.mem, dstAddrIdx,
			4 ));
    }
    return rv;
  }


  private int readMemByteInternal( int addr )
  {
    int rv = 0;
    if( (addr >= 0x0400) && (addr < 0x0800) ) {
      rv = this.sockets[ (addr >> 8) & 0x03 ].readMemByte( addr );
    } else {
      if( (addr >= 0) && (addr < this.mem.length) ) {
	rv = (int) this.mem[ addr ] & 0xFF;
      }
    }
    return rv;
  }


  private void releaseReservedDatagramSockets()
  {
    synchronized( this.reservedDatagramSockets ) {
      for( EmuDatagramSocket ds : this.reservedDatagramSockets ) {
	ds.close();
      }
      this.reservedDatagramSockets.clear();
    }
  }


  private void setMemByte( int addr, int value )
  {
    if( (addr >= 0) && (addr < this.mem.length) )
      this.mem[ addr ] = (byte) value;
  }


  private boolean setMemIpAddr( int addr, InetAddress inetAddr )
  {
    boolean done = false;
    if( inetAddr != null ) {
      byte[] ipAddr = inetAddr.getAddress();
      if( ipAddr != null ) {
	if( ipAddr.length == 4 ) {
	  for( int i = 0; i < ipAddr.length; i++ ) {
	    setMemByte( addr + i, ipAddr[ i ] );
	  }
	  done = true;
	}
      }
      if( !done && inetAddr.isLoopbackAddress() ) {
	setMemByte( addr, 127 );
	setMemByte( addr + 1, 0 );
	setMemByte( addr + 2, 0 );
	setMemByte( addr + 3, 1 );
	done = true;
      }
    }
    if( !done ) {
      for( int i = 0; i < 4; i++ ) {
	setMemByte( addr + i, 0 );
      }
    }
    return done;
  }


  private void setMemWord( int addr, int value )
  {
    if( (addr >= 0) && (addr < this.mem.length) ) {
      this.mem[ addr++ ] = (byte) (value >> 8);
      if( addr < this.mem.length ) {
	this.mem[ addr ] = (byte) value;
      }
    }
  }


  private static void wakeUpThread( Thread thread, boolean interrupt )
  {
    if( thread != null ) {
      if( interrupt ) {
	thread.interrupt();
      }
      synchronized( thread ) {
	try {
	  thread.notify();
	}
	catch( IllegalMonitorStateException ex ) {}
      }
    }
  }


  private void wakeUpThreads()
  {
    for( SocketData socket : this.sockets ) {
      wakeUpThread( socket.cmdThread, true );
      wakeUpThread( socket.recvThread, true );
    }
  }
}
