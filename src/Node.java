import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class Node implements Runnable{

    String id;
    double transmissionRange;
    double[] coordinate;
    Network network;
    private state current_state;
    private HashSet<Packet> acknowledgesPackets = new HashSet<>();

    public Node(String id, double transmissionRange, double[] coordinate, Network network) {
        this.id = id;
        this.transmissionRange = transmissionRange;
        this.coordinate = coordinate;
        this.network = network;
    }

    @Override
    public void run() {
        while(true) {
            try {
                Thread.sleep(1000);
                Optional<Packet> packet = this.network.receive(this);
                if (packet.isPresent()) {
                    System.out.println("received " + packet.get().type);
                    Packet reply = new Packet(PacketType.DS, this.coordinate, this.id, packet.get().originID, new HashSet<>());
                    network.send(reply);
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
            Packet ctsPacket = new Packet(PacketType.CTS, this.coordinate, this.id, packet.originID, new HashSet<>());
            this.network.send(ctsPacket);
            // Go to Wait for Data Send state
            this.current_state = state.WFDS;
        } else if (this.current_state == state.WFCTS && packet.type == PacketType.CTS && Objects.equals(packet.destination, this.id)) {
            // This corresponds to step 3
            // When in WFCTS state and receive CTS...
            // TODO cancel the waitForDataSendTimer. Maybe implement this by simply letting the timer run
            // TODO but also having a boolean for that type of timer that keeps track of whether it is still "enabled"
            Packet dsPacket = new Packet(PacketType.DS, this.coordinate, this.id, packet.originID, new HashSet<>());
            this.send(dsPacket);
            this.current_state = state.SendData;
            Packet dataPacket = new Packet(PacketType.DATA, this.coordinate, this.id, packet.originID, new HashSet<>());
            this.send(dataPacket);
            this.current_state = state.WFACK;
            // TODO start timer
        } else if (this.current_state == state.WFDS && packet.type == PacketType.DS) {
            // Step 4
            this.current_state = state.WFData;
            //TODO start timer
        } else if (this.current_state == state.WFData && packet.type == PacketType.DATA){
            // Step 5
            // TODO start timer
            Packet ackPacket = new Packet(PacketType.ACK, this.coordinate, this.id, packet.originID, new HashSet<>());
            this.acknowledgesPackets.add(ackPacket);
            this.send(ackPacket);
            this.current_state = state.IDLE;
        } else if (this.current_state == state.WFACK && packet.type == PacketType.ACK) {
            // Step 6
            // TODO reset timer
            this.current_state = state.IDLE;
        } else if (this.current_state == state.IDLE && packet.type == PacketType.RTS && this.acknowledgesPackets.contains(packet)) {
            // Step 7
            Packet ackPacket = new Packet(PacketType.ACK, this.coordinate, this.id, packet.originID, new HashSet<>());
            this.send(ackPacket);
        } else if (packet.type == PacketType.ACK && this.current_state == state.CONTEND) {
            // Step 8
            Packet ctsPacket = new Packet(PacketType.CTS, this.coordinate, this.id, packet.originID, new HashSet<>());
            this.send(ctsPacket);
            this.current_state = state.WFDS;
            // TODO start timer
        } else if (this.current_state == state.QUIET && packet.type == PacketType.RTS) {
            // Step 9
            this.current_state = state.WFCntend;
            // TODO start timer
        } else if (this.current_state == state.QUIET && packet.type == PacketType.CTS) {
            // Step 10
            this.current_state = state.WFCntend;
            // TODO start timer
        } else if (this.current_state == state.WFCntend && (packet.type == PacketType.CTS || packet.type == PacketType.RTS)) {
            // Step 11
            // TODO increase timer if necessary.
        } else if (this.current_state == state.WFRTS && packet.type == PacketType.RTS) {
            // Step 12
            Packet ctsPacket = new Packet(PacketType.CTS, this.coordinate, this.id, packet.originID, new HashSet<>());
            this.current_state = state.WFDS;
            // TODO set timer
        } else if (this.current_state == state.IDLE && packet.type == PacketType.RRTS) {
            Packet rtsPacket = new Packet(PacketType.RTS, this.coordinate, this.id, packet.originID, new HashSet<>());
            this.send(rtsPacket);
            this.current_state = state.WFCTS;
            // TODO set timer.
        }

    }

    // When a node wants to send something, it should call this method instead of directly calling the Network
    // send method. This has to do with the MACAW implementation.
    void send(Packet packet) {
        // Step 1 from paper
        if (this.current_state == state.IDLE) {

        }
    }

    void setWFDataTimer() {
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                // TODO implement timer expiration behaviour
            }
        };

        Timer timer = new Timer();
        // TODO Random timer, range might not make sense.
        timer.schedule(task, ThreadLocalRandom.current().nextInt(100, 1000));
    }

    void setDataSendTimer() {
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                // TODO implement timer expiration behaviour
            }
        };

        Timer timer = new Timer();
        // TODO Random timer, range might not make sense.
        timer.schedule(task, ThreadLocalRandom.current().nextInt(100, 1000));
    }


    void setWaitForDataSendTimer() {
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                // TODO implement timer expiration behaviour
            }
        };

        Timer timer = new Timer();
        // TODO Random timer, range might not make sense.
        timer.schedule(task, ThreadLocalRandom.current().nextInt(100, 1000));
    }

    // Timer associated with step 1 from paper
    void setContendTimer() {
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                // TODO implement timer expiration behaviour
            }
        };

        Timer timer = new Timer();
        // TODO Random timer, range might not make sense.
        timer.schedule(task, ThreadLocalRandom.current().nextInt(100, 1000));
    }
}