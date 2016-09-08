
const Events = {

  ACCEPT_TCP(fd)  { return "org.mhzed.nio.NetModule.accept." + fd },
  CONNECT_TCP(fd) { return "org.mhzed.nio.NetModule.connect." + fd },
  RECV_DATA(fd)   { return "org.mhzed.nio.NetModule.recvdata." + fd },
  RECV_ERR(fd)    { return "org.mhzed.nio.NetModule.recverr." + fd },
  RECV_END(fd)    { return "org.mhzed.nio.NetModule.recvend." + fd },
  SEND_ERR(fd)    { return "org.mhzed.nio.NetModule.senderr." + fd },
  PIPE_BYTES(fd)  { return "org.mhzed.nio.NetModule.pipebytes." + fd }
}

module.exports =  Events;