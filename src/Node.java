import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class Node implements Runnable{
    // this boolean keeps track of whether the transition to state.CONTEND was done based on sender initiated
    // or receiver initiated (RRTS)
    boolean senderInitiated = false;
    String communicatingWith;
    TimerTask task;

    int my_backoff;
    Map<String, Integer> local_backoff = new HashMap<>();
    Map<String, Integer> remote_backoff = new HashMap<>();
    Map<String, Integer> exchange_seq_number = new HashMap<>();
    Map<String, Integer> retry_count = new HashMap<>();
    String id;
    double transmissionRange;
    double[] coordinate;
    Network network;
    private state current_state = state.IDLE;
    private HashSet<Packet> acknowledgesPackets = new HashSet<>();

    public Node(String id, double transmissionRange, double[] coordinate, Network network) {
        this.id = id;
        this.transmissionRange = transmissionRange;
        this.coordinate = coordinate;
        this.network = network;
    }

    @Override
    public void run() {
        if (Objects.equals(this.id, "A")) {
            Packet rtsPacket = new Packet(PacketType.RTS, this.coordinate, this.id, "B", new HashSet<>(), this.local_backoff.getOrDefault("B", 0), this.remote_backoff.getOrDefault("B", 0), this.exchange_seq_number.getOrDefault("B", 0));
            this.senderInitiated = true;
            this.communicatingWith = "B";
            this.send(rtsPacket, this);
        }
        while(true) {
            try {
                Thread.sleep(100);
                Optional<Packet> packet = network.receive(this);
                if (packet.isPresent()) {
                    this.receive(packet.get());
                }
                if (Objects.equals(this.id, "C")) {
                    System.out.println("C STATE: " + this.current_state.toString());
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    // TODO in the following code, add conditions so that it is determinded whether this node is the recipient of a packet or simply an "observer"
    // This method deals with handling a received packet in an appropriate way.
    void receive(Packet packet) {
        if (this.current_state == state.IDLE && packet.type == PacketType.RTS && Objects.equals(packet.destination, this.id)) {
            // This corresponds to step 2 of the paper
            // if idle and receive RTS, send Clear to send
            reassignBackoffs(packet);
            Packet ctsPacket = new Packet(PacketType.CTS, this.coordinate, this.id, packet.originID, new HashSet<>(), this.local_backoff.get(packet.originID), this.remote_backoff.get(packet.originID), this.exchange_seq_number.get(packet.originID));
            this.network.send(ctsPacket);
            // Go to Wait for Data Send state
            this.current_state = state.WFDS;
        } else if (this.current_state == state.WFCTS && packet.type == PacketType.CTS && Objects.equals(packet.destination, this.id)) {
            // This corresponds to step 3
            // When in WFCTS state and receive CTS...
            task.cancel();
            reassignBackoffs(packet);

            Packet dsPacket = new Packet(PacketType.DS, this.coordinate, this.id, packet.originID, new HashSet<>(), this.local_backoff.get(packet.originID), this.remote_backoff.get(packet.originID), this.exchange_seq_number.get(packet.originID));
            this.send(dsPacket, this);
            this.current_state = state.SendData;
            Packet dataPacket = new Packet(PacketType.DATA, this.coordinate, this.id, packet.originID, new HashSet<>(), this.local_backoff.get(packet.originID), this.remote_backoff.get(packet.originID), this.exchange_seq_number.get(packet.originID));
            this.send(dataPacket, this);
            this.current_state = state.WFACK;
            setTimer(this, 200);
        } else if (this.current_state == state.WFDS && packet.type == PacketType.DS && Objects.equals(packet.destination, this.id)) {
            // Step 4
            reassignBackoffs(packet);
            this.current_state = state.WFData;
            setTimer(this, 200);
        } else if (this.current_state == state.WFData && packet.type == PacketType.DATA && Objects.equals(packet.destination, this.id)){
            // Step 5
            task.cancel();
            setTimer(this, 200);
            Packet ackPacket = new Packet(PacketType.ACK, this.coordinate, this.id, packet.originID, new HashSet<>(), this.local_backoff.get(packet.originID), this.remote_backoff.get(packet.originID), this.exchange_seq_number.get(packet.originID));
            this.acknowledgesPackets.add(ackPacket);
            this.send(ackPacket, this);
            this.current_state = state.IDLE;
        } else if (this.current_state == state.WFACK && packet.type == PacketType.ACK && Objects.equals(packet.destination, this.id)) {
            // Step 6
            reassignBackoffs(packet);
            task.cancel();
            this.current_state = state.IDLE;
        } else if (this.current_state == state.IDLE && packet.type == PacketType.RTS && this.acknowledgesPackets.contains(packet)) {
            // Step 7
            Packet ackPacket = new Packet(PacketType.ACK, this.coordinate, this.id, packet.originID, new HashSet<>(), this.local_backoff.get(packet.originID), this.remote_backoff.get(packet.originID), this.exchange_seq_number.get(packet.originID));
            this.send(ackPacket, this);
        } else if (packet.type == PacketType.ACK && this.current_state == state.CONTEND) {
            // Step 8
            Packet ctsPacket = new Packet(PacketType.CTS, this.coordinate, this.id, packet.originID, new HashSet<>(), this.local_backoff.get(packet.originID), this.remote_backoff.get(packet.originID), this.exchange_seq_number.get(packet.originID));
            this.send(ctsPacket, this);
            this.current_state = state.WFDS;
            setTimer(this, 200);
            //TODO This seems wrong!
        } else if (this.current_state == state.QUIET && packet.type == PacketType.RTS && Objects.equals(packet.destination, this.id)) {
            // Step 9
            this.current_state = state.WFCntend;
            setTimer(this, 200);
        } else if (this.current_state == state.QUIET && packet.type == PacketType.CTS && Objects.equals(packet.destination, this.id)) {
            // Step 10
            this.current_state = state.WFCntend;
            this.remote_backoff.put(packet.originID, packet.localBackoff);
            this.remote_backoff.put(packet.destination, packet.remoteBackoff);
            this.my_backoff = packet.localBackoff;
            setTimer(this, 200);
        } else if (this.current_state == state.WFCntend && (packet.type == PacketType.CTS || packet.type == PacketType.RTS && Objects.equals(packet.destination, this.id))) {
            // Step 11
            // TODO increase timer if necessary.
            setTimer(this, 200);
            if (packet.type != PacketType.RTS) {
                this.remote_backoff.put(packet.originID, packet.localBackoff);
                this.remote_backoff.put(packet.destination, packet.remoteBackoff);
                this.my_backoff = packet.localBackoff;
            }
        } else if (this.current_state == state.WFRTS && packet.type == PacketType.RTS) {
            // Step 12
            Packet ctsPacket = new Packet(PacketType.CTS, this.coordinate, this.id, packet.originID, new HashSet<>(), this.local_backoff.get(packet.originID), this.remote_backoff.get(packet.originID), this.exchange_seq_number.get(packet.originID));
            this.current_state = state.WFDS;
            setTimer(this, 200);
        } else if (this.current_state == state.IDLE && packet.type == PacketType.RRTS && Objects.equals(packet.destination, this.id)) {
            // Step 13
            reassignBackoffs(packet);
            Packet rtsPacket = new Packet(PacketType.RTS, this.coordinate, this.id, packet.originID, new HashSet<>(), this.my_backoff, this.remote_backoff.get(packet.originID), this.exchange_seq_number.get(packet.originID));
            this.send(rtsPacket, this);
            this.current_state = state.WFCTS;
            setTimer(this, 200);
        } else if (packet.type == PacketType.RTS && !Objects.equals(packet.destination, this.id)) {
            // Defer rule 1
            this.current_state = state.QUIET;
            setTimer(this, 5000);
            // TODO set a timer sufficient for A to hear B's CTS
        } else if (packet.type == PacketType.DS && !Objects.equals(packet.destination, this.id)) {
            // Defer rule 2
            this.current_state = state.QUIET;
            this.remote_backoff.put(packet.originID, packet.localBackoff);
            this.remote_backoff.put(packet.destination, packet.remoteBackoff);
            this.my_backoff = packet.localBackoff;
            setTimer(this, 5000);
            // TODO set a timer sufficient for A to transmit data and hear B's ack
        } else if (packet.type == PacketType.CTS && !Objects.equals(packet.destination, this.id)) {
            // Defer rule 3
            this.remote_backoff.put(packet.originID, packet.localBackoff);
            this.remote_backoff.put(packet.destination, packet.remoteBackoff);
            this.current_state = state.QUIET;
            this.my_backoff = packet.localBackoff;
            setTimer(this, 5000);
            // TODO set a timer suffecient for B to hear A's data.
        } else if (packet.type == PacketType.RRTS && !Objects.equals(packet.destination, this.id)) {
            // Defer rule 4
            this.remote_backoff.put(packet.originID, packet.localBackoff);
            this.remote_backoff.put(packet.destination, packet.remoteBackoff);
            this.current_state = state.QUIET;
            this.my_backoff = packet.localBackoff;
            setTimer(this, 5000);
            // TODO set a timer sufficient for an RTS-CTS exchange.
        }

    }

    void reassignBackoffs(Packet packet) {
        if (packet.sequenceNumber > this.exchange_seq_number.getOrDefault(packet.originID, 0)) {
            this.local_backoff.put(packet.originID, packet.remoteBackoff);
            this.remote_backoff.put(packet.originID, packet.localBackoff);

            Integer previousSeq = this.exchange_seq_number.getOrDefault(packet.originID, 0);
            this.exchange_seq_number.put(packet.originID, previousSeq + 1);
            this.retry_count.put(packet.originID, 1);
        } else {
            // Packet is a retransmission
            this.local_backoff.put(packet.originID, packet.localBackoff + packet.remoteBackoff - this.remote_backoff.getOrDefault(packet.originID, 0));
        }
    }

    // When a node wants to send something, it should call this method instead of directly calling the Network
    // send method. This has to do with the MACAW implementation.
    void send(Packet packet, Node node) {
        // Step 1 from paper
        if (this.current_state == state.IDLE) {
            this.current_state = state.CONTEND;
            setTimer(node, 200);
        } else {
            network.send(packet);
        }
    }

    void setTimer(Node node, long duration) {
        if (task != null) {
            task.cancel();
        }
        task = new TimerTask() {
            @Override
            public void run() {
                System.out.println("Timer expires in " + node.id);
                if (node.current_state == state.WFCntend) {
                    if (node.id == "C") {
                        System.out.println("C IS HERE 1 ============================");
                    }
                    // first timeout rule
                    node.setTimer(node, 200);
                    node.current_state = state.CONTEND;
                } else if (node.current_state == state.CONTEND) {
                    if (node.id == "C") {
                        System.out.println("C IS HERE 2 ============================");
                    }
                    // second timeout rule
                    // TODO this part is why C does not go back to IDLE.
                    if (node.senderInitiated) {
                        if (node.id == "C") {
                            System.out.println("C IS HERE 3 ============================");
                        }
                        Packet rtsPacket = new Packet(PacketType.RTS, node.coordinate, node.id, node.communicatingWith, new HashSet<>(), node.local_backoff.getOrDefault(node.communicatingWith, 0), node.remote_backoff.getOrDefault(node.communicatingWith, 0), node.exchange_seq_number.getOrDefault(node.communicatingWith, 0));
                        node.send(rtsPacket, node);
                        node.current_state = state.WFCTS;
                        node.setTimer(node, 200);
                    } else {
                        if (node.id == "C") {
                            //TODO the boolean senderInitiated does not work properly. It should be possible to account for C simply being an onlooker.
                            System.out.println("C IS HERE 4 ============================");
                        }
                       Exception e = new Exception("RRTS later");
                       e.printStackTrace();
                    }
                } else {
                    if (node.id == "C") {
                        System.out.println("C IS HERE 5 ============================");
                    }
                    node.current_state = state.IDLE;
                }
            }
        };

        Timer timer = new Timer();
        // TODO Random timer, range might not make sense.
        timer.schedule(task, ThreadLocalRandom.current().nextInt((int) duration, (int) duration + 1));
    }

}