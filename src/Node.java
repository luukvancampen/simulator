import java.util.HashSet;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ThreadLocalRandom;

public class Node implements Runnable{

    String id;
    double transmissionRange;
    double[] coordinate;
    Network network;
    private state current_state;

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

    // This method deals with handling a received packet in an appropriate way.
    void receive(Packet packet) {
        if (this.current_state == state.IDLE && packet.type == PacketType.RTS) {
            // This corresponds to step 2 of the paper
            // if idle and receive RTS, send Clear to send
            Packet ctsPacket = new Packet(PacketType.CTS, this.coordinate, this.id, packet.originID, new HashSet<>());
            this.network.send(ctsPacket);
            // Go to Wait for Data Send state
            this.current_state = state.WFDS;
        } else if (this.current_state == state.WFCTS && packet.type == PacketType.CTS) {
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