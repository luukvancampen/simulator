import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;

public class Node implements Runnable {
    // Network / MAC fields.
    String id;
    double[] coordinate;
    double range;
    Network network;

    // DSR fields.
    private Map<String, List<RouteCacheEntry>> routeCache;
    private ArrayDeque<SendBufferEntry> sendBuffer;
    private Map<String, RouteRequestTableEntry> routeRequestTable;
    private List<GratituousReplyTableEntry> gratituousReplyTable;
    // TODO blacklist?
    private int routeRequestIdentificationCounter;

    Node(String id, double[] coordinate, double range, Network network) {
        this.id = id;
        this.coordinate = coordinate;
        this.range = range;
        this.network = network;

        routeCache = new HashMap<>();
        sendBuffer = new ArrayDeque<>();
        routeRequestTable = new HashMap<>();
        gratituousReplyTable = new ArrayList<>();
        routeRequestIdentificationCounter = 0;
    }

    @Override
    public void run() {
        while (true) {
            try {
                Thread.sleep(5);

                Optional<Packet> maybePacket = this.receive();

                if (maybePacket.isPresent()) {
                    System.out.println("PACKET RECEIVED");
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public Optional<Packet> receive() {
        Optional<Packet> maybePacket = network.receive(this);

        if (!maybePacket.isPresent()) {
            return Optional.empty();
        }

        Packet packet = maybePacket.get();

        if (packet.macDestination != id && packet.macDestination != "*") {
            return Optional.empty();
        }

        return receivePacket(maybePacket.get());
    }

    public void send(String receiver, String data) {
        Packet packet = new Packet();

        packet.ipSource = id;
        packet.ipDestination = receiver;

        packet.optionTypes = Set.of();
        packet.data = data;

        originatePacket(packet, false);
    }

    private void originatePacket(Packet packet, boolean piggyBackRouteRequest) {
        Optional<List<String>> maybeRoute = findRoute(packet.ipDestination);

        if (maybeRoute.isPresent()) {
            List<String> sourceRoute = maybeRoute.get();
            sendWithSourceRoute(packet, sourceRoute);
        } else {
            routeDiscovery(packet, piggyBackRouteRequest);
        }
    }

    private void sendWithSourceRoute(Packet packet, List<String> route) {
        packet.sourceCoordinate = coordinate;
        packet.received = new HashSet<>();

        packet.macSource = id;

        if (!route.isEmpty()) {
            packet.macDestination = route.get(0);

            packet.optionTypes = Set.of(OptionType.SourceRoute);
            packet.sourceRoute = route;
            packet.segmentsLeft = route.size();
        } else {
            packet.macDestination = packet.ipDestination;
        }

        // TODO 8.3.0

        network.send(packet);
    }

    private Optional<Packet> receivePacket(Packet packet) {
        if (packet.ipSource == id) {
            return Optional.empty();
        }

        if (packet.optionTypes.contains(OptionType.RouteRequest)) {
            List<String> sourceRoute = new ArrayList<>();
            sourceRoute.add(packet.ipSource);
            sourceRoute.addAll(packet.sourceRoute);
            sourceRoute.add(id);
            updateRoutingCache(sourceRoute);

            if (packet.targetAddress == id) {
                sourceRoute = new ArrayList<>();
                sourceRoute.addAll(packet.sourceRoute);
                sourceRoute.add(packet.targetAddress);
                sendRouteReply(id, packet.ipSource, sourceRoute, packet.identification);
            } else {
                if (packet.sourceRoute.contains(id)) {
                    return Optional.empty();
                }

                // TODO maybe blacklist

                if (routeRequestInTable(packet)) {
                    return Optional.empty();
                }

                addRouteRequestEntry(packet);

                boolean sentCachedRoute = false;

                Optional<List<String>> maybeCachedRoute = findRoute(packet.targetAddress);

                if (maybeCachedRoute.isPresent()) {
                    List<String> cachedRoute = maybeCachedRoute.get();

                    boolean containsDuplicate = false;

                    if (cachedRoute.contains(packet.ipSource)) {
                        containsDuplicate = true;
                    }

                    for (String node : packet.sourceRoute) {
                        if (cachedRoute.contains(node)) {
                            containsDuplicate = true;
                            break;
                        }
                    }

                    if (!containsDuplicate) {
                        sourceRoute = new ArrayList<>();
                        sourceRoute.addAll(packet.sourceRoute);
                        sourceRoute.add(id);
                        sourceRoute.addAll(cachedRoute);
                        sourceRoute.add(packet.targetAddress);

                        // TODO 8.2.5

                        sendRouteReply(id, packet.ipSource, sourceRoute, packet.identification);

                        sentCachedRoute = true;

                        // TODO might need to propagate RouteRequest if other options present.
                    }
                }

                if (!sentCachedRoute) {
                    sourceRoute = new ArrayList<>();
                    sourceRoute.addAll(packet.sourceRoute);
                    sourceRoute.add(id);

                    Packet routeRequestPacket = new Packet();
                    routeRequestPacket.sourceCoordinate = coordinate;
                    routeRequestPacket.received = new HashSet<>();

                    routeRequestPacket.macSource = id;
                    routeRequestPacket.macDestination = "*";

                    routeRequestPacket.ipSource = packet.ipSource;
                    routeRequestPacket.ipDestination = "255.255.255.255";
                    routeRequestPacket.timeToLive = 255;

                    routeRequestPacket.optionTypes = Set.of(OptionType.RouteRequest);
                    routeRequestPacket.sourceRoute = sourceRoute;
                    routeRequestPacket.identification = packet.identification;
                    routeRequestPacket.targetAddress = packet.targetAddress;

                    network.send(routeRequestPacket);
                }
            }
        }

        if (packet.optionTypes.contains(OptionType.RouteReply)) {
            List<String> sourceRoute = new ArrayList<>();
            sourceRoute.add(packet.ipDestination);
            sourceRoute.addAll(packet.sourceRoute);

            updateRoutingCache(sourceRoute);

            if (packet.ipDestination != id) {
                sendRouteReply(packet.ipSource, packet.ipDestination, packet.sourceRoute,
                        packet.identification);
            }
        }

        if (packet.optionTypes.contains(OptionType.RouteError)) {
            // TODO 8.3.5
        }

        if (packet.optionTypes.contains(OptionType.AcknowledgementRequest)) {
            // TODO 8.3.3
        }

        if (packet.optionTypes.contains(OptionType.Acknowledgement)) {
            List<String> link = List.of(packet.ipSource, packet.ipDestination);
            updateRoutingCache(link);

            // TODO 8.3.3
        }

        if (packet.optionTypes.contains(OptionType.SourceRoute)) {
            List<String> route = new ArrayList<>();
            route.add(packet.ipSource);
            route.addAll(packet.sourceRoute);
            route.add(packet.ipDestination);
            updateRoutingCache(route);

            // TODO 8.1.5
            // TODO automatic route shortening

            if (packet.segmentsLeft == 1) {
                packet.segmentsLeft -= 1;

                if (packet.ipDestination != "255.255.255.255") {
                    packet.timeToLive -= 1;

                    // TODO 8.3

                    packet.macSource = id;
                    packet.macDestination = packet.ipDestination;
                    packet.sourceCoordinate = coordinate;
                    packet.received = new HashSet<>();

                    network.send(packet);
                }
            } else if (packet.segmentsLeft > 1) {
                packet.segmentsLeft -= 1;
                int i = packet.sourceRoute.size() - packet.segmentsLeft;

                if (packet.sourceRoute.get(i) != "255.255.255.255" && packet.ipDestination != "255.255.255.255") {
                    packet.timeToLive -= 1;

                    // TODO 8.3

                    packet.macSource = id;
                    packet.macDestination = packet.sourceRoute.get(i);
                    packet.sourceCoordinate = coordinate;
                    packet.received = new HashSet<>();

                    network.send(packet);
                }
            }

            if (packet.ipDestination == id) {
                // packet.optionTypes.remove(OptionType.SourceRoute);

                return Optional.of(packet);
            }
        }

        return Optional.empty();
    }

    private void sendRouteReply(String sourceAddress, String destinationAddress, List<String> sourceRoute,
            int routeRequestIdentification) {
        Packet routeReplyPacket = new Packet();

        routeReplyPacket.sourceCoordinate = coordinate;
        routeReplyPacket.received = new HashSet<>();

        routeReplyPacket.macSource = id;

        if (sourceRoute.size() >= 2) {
            routeReplyPacket.macDestination = sourceRoute.get(sourceRoute.size() - 2);
        } else {
            routeReplyPacket.macDestination = destinationAddress;
        }

        routeReplyPacket.ipSource = sourceAddress;
        routeReplyPacket.ipDestination = destinationAddress;
        routeReplyPacket.timeToLive = 255;

        routeReplyPacket.optionTypes = Set.of(OptionType.RouteReply);
        routeReplyPacket.sourceRoute = sourceRoute;
        routeReplyPacket.identification = routeRequestIdentification;

        // TODO sleep between 0 and BroadcastJitter. 8.2.4

        originatePacket(routeReplyPacket, true);
    }

    private void addRouteRequestEntry(Packet routeRequestPacket) {
        RouteRequestId routeRequestId = new RouteRequestId();
        routeRequestId.routeRequestIdentification = routeRequestPacket.identification;
        routeRequestId.targetAddress = routeRequestPacket.targetAddress;

        RouteRequestTableEntry routeRequestTableEntry = routeRequestTable.getOrDefault(id,
                new RouteRequestTableEntry());

        if (routeRequestTableEntry.routeRequests == null) {
            routeRequestTableEntry.consecutiveRequests = 0;
            routeRequestTableEntry.timeRemainingUntilNextRequest = 0;
            routeRequestTableEntry.routeRequests = new ArrayDeque<>();
        }

        routeRequestTableEntry.timeToLive = routeRequestPacket.timeToLive;
        routeRequestTableEntry.consecutiveRequests += 1;
        routeRequestTableEntry.routeRequests.add(routeRequestId);
    }

    private void updateRoutingCache(List<String> route) {
        String sourceAddress = route.get(0);

        for (int i = 1; i < route.size(); i++) {
            String destinationAddress = route.get(i);
            List<RouteCacheEntry> neighbours = routeCache.getOrDefault(sourceAddress, new ArrayList<>());

            for (int j = 0; j < neighbours.size(); j++) {
                if (neighbours.get(j).destinationAddress == destinationAddress) {
                    neighbours.remove(j);
                }
            }

            RouteCacheEntry routeCacheEntry = new RouteCacheEntry();
            routeCacheEntry.destinationAddress = destinationAddress;
            neighbours.add(routeCacheEntry);

            routeCache.put(sourceAddress, neighbours);

            sourceAddress = destinationAddress;
        }

        System.out.println(id + " :: " + routeCache);

        checkSendBuffer();
    }

    private void checkSendBuffer() {
        for (SendBufferEntry sendBufferEntry : sendBuffer) {
            Optional<List<String>> maybeRoute = findRoute(sendBufferEntry.packet.ipDestination);

            if (maybeRoute.isPresent()) {
                List<String> sourceRoute = maybeRoute.get();
                sendWithSourceRoute(sendBufferEntry.packet, sourceRoute);

                sendBuffer.remove(sendBufferEntry);
            }
        }
    }

    private void routeDiscovery(Packet packet, boolean piggyBackRouteRequest) {
        Packet routeRequestPacket = new Packet();

        routeRequestPacket.sourceCoordinate = coordinate;
        routeRequestPacket.received = new HashSet<>();

        routeRequestPacket.macSource = id;
        routeRequestPacket.macDestination = "*";

        routeRequestPacket.ipSource = id;
        routeRequestPacket.ipDestination = "255.255.255.255";
        routeRequestPacket.timeToLive = 255;

        routeRequestPacket.optionTypes = Set.of(OptionType.RouteRequest);
        routeRequestPacket.sourceRoute = new ArrayList<>();
        routeRequestPacket.identification = routeRequestIdentificationCounter;
        routeRequestPacket.targetAddress = packet.ipDestination;

        routeRequestIdentificationCounter = (routeRequestIdentificationCounter + 1) % Integer.MAX_VALUE;

        addRouteRequestEntry(routeRequestPacket);

        // TODO rate limit route requests

        if (!piggyBackRouteRequest) {
            SendBufferEntry sendBufferEntry = new SendBufferEntry();
            sendBufferEntry.packet = packet;
            sendBuffer.add(sendBufferEntry);
        } else {
            routeRequestPacket.piggyBack = packet;
        }

        network.send(routeRequestPacket);
    }

    private boolean routeRequestInTable(Packet routeRequestPacket) {
        RouteRequestTableEntry routeRequestTableEntry = routeRequestTable.get(routeRequestPacket.ipSource);

        if (routeRequestTableEntry != null) {
            for (RouteRequestId id : routeRequestTableEntry.routeRequests) {
                if (id.routeRequestIdentification == routeRequestPacket.identification
                        && id.targetAddress == routeRequestPacket.targetAddress) {
                    return true;
                }
            }
        }

        return false;
    }

    private Optional<List<String>> findRoute(String destination) {
        Map<String, Integer> hops = new HashMap<>();
        Map<String, String> previous = new HashMap<>();

        List<String> queue = new ArrayList<>();

        for (String node : routeCache.keySet()) {
            if (node == id) {
                hops.put(node, 0);
            } else {
                hops.put(node, Integer.MAX_VALUE);
            }

            previous.put(node, null);
            queue.add(node);
        }

        while (!queue.isEmpty()) {
            String node = queue.get(0);
            int minHops = hops.get(node);
            int index = 0;

            for (int i = 1; i < queue.size(); i++) {
                String queuedNode = queue.get(i);
                int h = hops.get(queuedNode);

                if (h < minHops) {
                    minHops = h;
                    node = queuedNode;
                    index = i;
                }
            }

            queue.remove(index);

            for (RouteCacheEntry neighbour : routeCache.get(node)) {
                if (queue.contains(neighbour.destinationAddress)) {
                    int newHops = hops.get(node) + 1;

                    if (newHops < hops.get(neighbour.destinationAddress)) {
                        hops.put(neighbour.destinationAddress, newHops);
                        previous.put(neighbour.destinationAddress, node);

                        if (neighbour.destinationAddress == destination) {
                            queue.clear();
                        }
                    }
                }
            }
        }

        List<String> route = new ArrayList<>();

        String currentNode = destination;

        while (currentNode != id) {
            String prev = previous.get(currentNode);

            if (prev == null) {
                return Optional.empty();
            }

            currentNode = prev;
            route.add(0, currentNode);
        }

        route.remove(0);

        return Optional.of(route);
    }

    private final class RouteCacheEntry {
        private String destinationAddress;
        private int timeout;

        @Override
        public String toString() {
            return destinationAddress;
        }
    }

    private final class SendBufferEntry {
        private Packet packet;
        private int additionTime;
    }

    private final class RouteRequestTableEntry {
        private int timeToLive;
        private int consecutiveRequests;
        private int timeRemainingUntilNextRequest;
        private Queue<RouteRequestId> routeRequests;
    }

    private final class RouteRequestId {
        private int routeRequestIdentification;
        private String targetAddress;
    }

    private final class GratituousReplyTableEntry {
        private String targetAddress;
        private String sourceAddress;
        private int timeRemaining;
    }

    // Set<String> nodes = new HashSet<>();
    // nodes.add(id);
    // nodes.add(destination);

    // for (Link link : routeCache) {
    // nodes.add(link.source);
    // nodes.add(link.destination);
    // }

    // Map<String, Integer> hops = new HashMap<>();
    // Map<String, String> previous = new HashMap<>();

    // List<String> queue = new ArrayList<>();

    // for (String node : nodes) {
    // if (node == id) {
    // hops.put(node, 0);
    // } else {
    // hops.put(node, Integer.MAX_VALUE);
    // }

    // previous.put(node, null);
    // queue.add(node);
    // }

    // while (!queue.isEmpty()) {
    // String node = queue.get(0);
    // int minHops = hops.get(node);
    // int index = 0;

    // for (int i = 1; i < queue.size(); i++) {
    // String queuedNode = queue.get(i);
    // int h = hops.get(queuedNode);

    // if (h < minHops) {
    // minHops = h;
    // node = queuedNode;
    // index = i;
    // }
    // }

    // queue.remove(index);

    // for (Link link : routeCache) {
    // String neighbour = null;

    // if (link.source == node) {
    // neighbour = link.destination;
    // } else if (link.destination == node) {
    // neighbour = link.source;
    // }

    // if (neighbour != null && queue.contains(neighbour)) {
    // int newHops = hops.get(node) + 1;

    // if (newHops < hops.get(neighbour)) {
    // hops.put(neighbour, newHops);
    // previous.put(neighbour, node);

    // if (neighbour == destination) {
    // queue.clear();
    // }
    // }
    // }
    // }
    // }

    // String currentNode = destination;
    // List<String> route = new ArrayList<>();
    // route.add(currentNode);

    // while (currentNode != id) {
    // String prev = previous.get(currentNode);

    // if (prev == null) {
    // return Optional.empty();
    // }

    // currentNode = prev;
    // route.add(0, currentNode);
    // }

    // return Optional.of(route);

    // private final class Link {
    // String source;
    // String destination;

    // public boolean matches(Link link) {
    // return source == link.source && destination == link.destination
    // || source == link.destination && destination == link.source;
    // }
    // }

    // AAAA //

    // void send(String receiver, String data) {
    // Packet packet = new Packet();
    // packet.macSource = id;
    // packet.ipSource = id;
    // packet.ipDestination = receiver;
    // packet.optionTypes = new ArrayList<>();
    // packet.sourceCoordinate = coordinate;
    // packet.received = new HashSet<>();
    // packet.data = data;

    // sendPacket(packet);
    // }

    // private void sendPacket(Packet packet) {
    // Optional<List<String>> maybeRoute = findRoute(packet.ipDestination);

    // if (maybeRoute.isPresent()) {
    // List<String> route = maybeRoute.get();
    // packet.macDestination = route.get(1);

    // network.send(packet);
    // } else {
    // // Do route discovery.
    // sendBuffer.add(packet);

    // Packet routeRequestPacket = new Packet();
    // routeRequestPacket.macSource = id;
    // routeRequestPacket.macDestination = "*";
    // routeRequestPacket.ipSource = id;
    // routeRequestPacket.ipDestination = "*";
    // routeRequestPacket.optionTypes = List.of(OptionType.RouteRequest);
    // routeRequestPacket.sourceCoordinate = coordinate;
    // routeRequestPacket.target = packet.ipDestination;
    // routeRequestPacket.sequenceNumber = currentSequenceNumber;
    // routeRequestPacket.received = new HashSet<>();
    // routeRequestPacket.route = new ArrayList<>();

    // routeRequestPacket.route.add(id);
    // currentSequenceNumber = (currentSequenceNumber + 1) % Integer.MAX_VALUE;

    // network.send(routeRequestPacket);
    // }
    // }

    // Optional<Packet> receive() {
    // Optional<Packet> maybePacket = network.receive(this);

    // if (!maybePacket.isPresent()) {
    // return Optional.empty();
    // }

    // Packet packet = maybePacket.get();

    // if (packet.macDestination == id || packet.macDestination == "*") {
    // if (packet.optionTypes.isEmpty()) {
    // if (packet.ipDestination == id) {
    // // Successfully received a packet.
    // return Optional.of(packet);
    // } else {
    // Packet newPacket = new Packet();
    // newPacket.macSource = id;
    // newPacket.ipSource = packet.ipSource;
    // newPacket.ipDestination = packet.ipDestination;
    // newPacket.optionTypes = new ArrayList<>();
    // newPacket.sourceCoordinate = coordinate;
    // newPacket.received = new HashSet<>();
    // newPacket.data = packet.data;

    // sendPacket(newPacket);
    // }
    // }

    // for (OptionType type : packet.optionTypes) {
    // if (type == OptionType.RouteRequest) {
    // if (!packet.route.contains(id)) {
    // List<String> tempRoute = new ArrayList<>(packet.route);
    // tempRoute.add(id);

    // // Add current route to cache.
    // updateCache(tempRoute);
    // }

    // if (packet.target == id) {
    // // Send a RouteReply if we are the target for the RouteRequest.
    // Packet routeReplyPacket = new Packet();
    // routeReplyPacket.optionTypes = List.of(OptionType.RouteReply);
    // routeReplyPacket.macSource = id;
    // routeReplyPacket.macDestination = packet.route.get(packet.route.size() - 1);
    // routeReplyPacket.ipDestination = packet.route.get(0);
    // routeReplyPacket.ipSource = id;
    // routeReplyPacket.route = new ArrayList<>(packet.route);
    // routeReplyPacket.received = new HashSet<>();
    // routeReplyPacket.sourceCoordinate = coordinate;

    // routeReplyPacket.route.add(id);

    // sendPacket(routeReplyPacket);
    // } else {
    // // Forward the RouteRequest if our address is not yet in the current route.
    // if (!packet.route.contains(id)) {
    // Packet routeRequestPacket = new Packet();
    // routeRequestPacket.macSource = id;
    // routeRequestPacket.macDestination = "*";
    // routeRequestPacket.ipSource = packet.ipSource;
    // routeRequestPacket.ipDestination = "*";
    // routeRequestPacket.optionTypes = List.of(OptionType.RouteRequest);
    // routeRequestPacket.target = packet.target;
    // routeRequestPacket.route = new ArrayList<>(packet.route);
    // routeRequestPacket.received = new HashSet<>();
    // routeRequestPacket.sourceCoordinate = coordinate;

    // routeRequestPacket.route.add(id);

    // network.send(routeRequestPacket);
    // }
    // }
    // } else if (type == OptionType.RouteReply) {
    // updateCache(new ArrayList<>(packet.route));

    // if (packet.ipDestination == id) {
    // checkSendbuffer();
    // } else {
    // Packet newPacket = new Packet();
    // newPacket.macSource = id;
    // newPacket.ipSource = packet.ipSource;
    // newPacket.ipDestination = packet.ipDestination;
    // newPacket.optionTypes = List.of(OptionType.RouteReply);
    // newPacket.received = new HashSet<>();
    // newPacket.route = new ArrayList<>(packet.route);
    // newPacket.sourceCoordinate = coordinate;

    // sendPacket(newPacket);
    // }
    // } else if (type == OptionType.RouteError) {

    // } else if (type == OptionType.AcknowledgeRequest) {

    // } else if (type == OptionType.Acknowledge) {

    // } else if (type == OptionType.SourceRoute) {

    // }
    // }

    // }

    // return Optional.empty();
    // }

    // private void checkSendbuffer() {
    // for (int i = 0; i < sendBuffer.size(); i++) {
    // Packet packet = sendBuffer.get(i);

    // Optional<List<String>> maybeRoute = findRoute(packet.ipDestination);

    // if (maybeRoute.isPresent()) {
    // List<String> route = maybeRoute.get();
    // packet.macDestination = route.get(1);
    // network.send(packet);

    // sendBuffer.remove(i);
    // }
    // }
    // }

    // private void updateCache(List<String> route) {
    // String source = route.get(0);

    // for (int i = 1; i < route.size(); i++) {
    // Link link = new Link();
    // link.source = source;
    // link.destination = route.get(i);

    // boolean matches = false;

    // for (Link otherLink : routeCache) {
    // if (otherLink.matches(link)) {
    // matches = true;
    // break;
    // }
    // }

    // if (!matches) {
    // routeCache.add(link);
    // }

    // source = route.get(i);
    // }
    // }

    // // Use Dijkstra's algorithm to find the shortest route based on our
    // routeCache.

    // private final class Link {
    // String source;
    // String destination;

    // public boolean matches(Link link) {
    // return source == link.source && destination == link.destination
    // || source == link.destination && destination == link.source;
    // }
    // }

    // // this boolean keeps track of whether the transition to state.CONTEND was
    // done based on sender initiated
    // // or receiver initiated (RRTS)
    // // The army papers models this using two different CONTEND states.
    // boolean senderInitiated = false;
    // boolean receiverInitiated = false;
    // String communicatingWith;
    // TimerTask task;

    // int my_backoff;
    // Map<String, Integer> local_backoff = new HashMap<>();
    // Map<String, Integer> remote_backoff = new HashMap<>();
    // Map<String, Integer> exchange_seq_number = new HashMap<>();
    // Map<String, Integer> retry_count = new HashMap<>();
    // String id;
    // double transmissionRange;
    // double[] coordinate;
    // Network network;
    // String dataToSend = "";
    // private state current_state = state.IDLE;
    // private HashSet<Packet> acknowledgesPackets = new HashSet<>();

    // public Node(String id, double transmissionRange, double[] coordinate, Network
    // network) {
    // this.id = id;
    // this.transmissionRange = transmissionRange;
    // this.coordinate = coordinate;
    // this.network = network;
    // }

    // public state getCurrent_state() {
    // return this.current_state;
    // }

    // @Override
    // public void run() {
    // if (Objects.equals(this.id, "A")) {
    // // Packet rtsPacket = new Packet(PacketType.RTS, this.coordinate, this.id,
    // "B", new HashSet<>(), this.local_backoff.getOrDefault("B", 0),
    // this.remote_backoff.getOrDefault("B", 0),
    // this.exchange_seq_number.getOrDefault("B", 0));
    // // this.senderInitiated = true;
    // // this.communicatingWith = "B";
    // // this.macawSend(rtsPacket, this);
    // }
    // while (true) {
    // try {
    // Thread.sleep(5);
    // Optional<Packet> maybePacket = this.receive();
    // if (maybePacket.isPresent()) {
    // System.out.println("PACKET RECEIVED: " + maybePacket.get().data);
    // }
    // this.senderInitiated = false;
    // this.receiverInitiated = false;
    // } catch (InterruptedException e) {
    // throw new RuntimeException(e);
    // }
    // }
    // }

    // void send(String receiver, String data) {
    // //TODO throw exception when in quiet state.
    // System.out.println("Sending " + data + " to " + receiver);
    // this.senderInitiated = true;
    // this.dataToSend = data;
    // this.communicatingWith = receiver;
    // this.macawSend(new Packet(PacketType.CTS, this.coordinate, this.id, "", new
    // HashSet<>(), this.local_backoff.getOrDefault("", 0),
    // this.remote_backoff.getOrDefault("", 0),
    // this.exchange_seq_number.getOrDefault("", 0)), this);
    // }

    // Optional<Packet> receive() {
    // Optional<Packet> packet = network.receive(this);
    // Optional<Packet> macawPacket = Optional.empty();
    // if (packet.isPresent()) {
    // macawPacket = this.macawReceive(packet.get());
    // }
    // return macawPacket;
    // }

    // // Something weird is going on. In the specification in the paper it is
    // stated that when a node sends RRTS, it goes to WFDS state. However, upon
    // receiving the RRTS packet,
    // // the receiving node will send back an RTS packet. But, as soon as that RTS
    // arrives, the other node is still in WFDS state.

    // // TODO in the following code, add conditions so that it is determinded
    // whether this node is the recipient of a packet or simply an "observer"
    // // This method deals with handling a received packet in an appropriate way.
    // Optional<Packet> macawReceive(Packet packet) {
    // if (this.current_state == state.IDLE && packet.type == PacketType.RTS &&
    // Objects.equals(packet.destination, this.id)) {
    // // This corresponds to step 2 of the paper
    // // if idle and receive RTS, send Clear to send
    // reassignBackoffs(packet);
    // Packet ctsPacket = new Packet(PacketType.CTS, this.coordinate, this.id,
    // packet.originID, new HashSet<>(),
    // this.local_backoff.getOrDefault(packet.originID, 0),
    // this.remote_backoff.getOrDefault(packet.originID, 0),
    // this.exchange_seq_number.getOrDefault(packet.originID, 0));
    // this.network.send(ctsPacket);
    // // Go to Wait for Data Send state
    // this.current_state = state.WFDS;
    // } else if (this.current_state == state.WFCTS && packet.type == PacketType.CTS
    // && Objects.equals(packet.destination, this.id)) {
    // // This corresponds to step 3
    // // When in WFCTS state and receive CTS...
    // task.cancel();
    // reassignBackoffs(packet);

    // Packet dsPacket = new Packet(PacketType.DS, this.coordinate, this.id,
    // packet.originID, new HashSet<>(),
    // this.local_backoff.getOrDefault(packet.originID, 0),
    // this.remote_backoff.getOrDefault(packet.originID, 0),
    // this.exchange_seq_number.getOrDefault(packet.originID, 0));
    // this.macawSend(dsPacket, this);
    // this.current_state = state.SendData;
    // Packet dataPacket = new Packet(PacketType.DATA, this.coordinate, this.id,
    // packet.originID, new HashSet<>(),
    // this.local_backoff.getOrDefault(packet.originID, 0),
    // this.remote_backoff.getOrDefault(packet.originID, 0),
    // this.exchange_seq_number.getOrDefault(packet.originID, 0), dataToSend);
    // this.macawSend(dataPacket, this);
    // this.current_state = state.WFACK;
    // setTimer(this, 200);
    // } else if (this.current_state == state.WFDS && packet.type == PacketType.DS
    // && Objects.equals(packet.destination, this.id)) {
    // // Step 4
    // reassignBackoffs(packet);
    // this.current_state = state.WFData;
    // setTimer(this, 200);
    // } else if (this.current_state == state.WFData && packet.type ==
    // PacketType.DATA && Objects.equals(packet.destination, this.id)) {
    // // Step 5
    // task.cancel();
    // setTimer(this, 200);
    // Packet ackPacket = new Packet(PacketType.ACK, this.coordinate, this.id,
    // packet.originID, new HashSet<>(),
    // this.local_backoff.getOrDefault(packet.originID, 0),
    // this.remote_backoff.getOrDefault(packet.originID, 0),
    // this.exchange_seq_number.getOrDefault(packet.originID, 0));
    // this.acknowledgesPackets.add(ackPacket);
    // this.macawSend(ackPacket, this);
    // this.current_state = state.IDLE;
    // return Optional.of(packet);
    // } else if (this.current_state == state.WFACK && packet.type == PacketType.ACK
    // && Objects.equals(packet.destination, this.id)) {
    // // Step 6
    // reassignBackoffs(packet);
    // task.cancel();
    // this.current_state = state.IDLE;
    // } else if (this.current_state == state.IDLE && packet.type == PacketType.RTS
    // && this.acknowledgesPackets.contains(packet)) {
    // // Step 7
    // Packet ackPacket = new Packet(PacketType.ACK, this.coordinate, this.id,
    // packet.originID, new HashSet<>(),
    // this.local_backoff.getOrDefault(packet.originID, 0),
    // this.remote_backoff.getOrDefault(packet.originID, 0),
    // this.exchange_seq_number.getOrDefault(packet.originID, 0));
    // this.macawSend(ackPacket, this);
    // } else if (packet.type == PacketType.ACK && this.current_state ==
    // state.CONTEND) {
    // // Step 8
    // Packet ctsPacket = new Packet(PacketType.CTS, this.coordinate, this.id,
    // packet.originID, new HashSet<>(),
    // this.local_backoff.getOrDefault(packet.originID, 0),
    // this.remote_backoff.getOrDefault(packet.originID, 0),
    // this.exchange_seq_number.getOrDefault(packet.originID, 0));
    // this.macawSend(ctsPacket, this);
    // this.current_state = state.WFDS;
    // setTimer(this, 200);
    // //TODO This seems wrong!
    // } else if (this.current_state == state.QUIET && packet.type == PacketType.RTS
    // && Objects.equals(packet.destination, this.id)) {
    // // Step 9
    // // This is transmission initiated by someone else.
    // this.communicatingWith = packet.originID;
    // this.receiverInitiated = true;
    // this.senderInitiated = false;
    // this.current_state = state.WFCntend;
    // setTimer(this, 200);
    // } else if (this.current_state == state.QUIET && packet.type == PacketType.CTS
    // && Objects.equals(packet.destination, this.id)) {
    // // Step 10
    // this.current_state = state.WFCntend;
    // this.remote_backoff.put(packet.originID, packet.localBackoff);
    // this.remote_backoff.put(packet.destination, packet.remoteBackoff);
    // this.my_backoff = packet.localBackoff;
    // setTimer(this, 200);
    // } else if (this.current_state == state.WFCntend && (packet.type ==
    // PacketType.CTS || packet.type == PacketType.RTS &&
    // Objects.equals(packet.destination, this.id))) {
    // // Step 11
    // // TODO increase timer if necessary.
    // setTimer(this, 200);
    // if (packet.type != PacketType.RTS) {
    // this.remote_backoff.put(packet.originID, packet.localBackoff);
    // this.remote_backoff.put(packet.destination, packet.remoteBackoff);
    // this.my_backoff = packet.localBackoff;
    // }
    // } else if (this.current_state == state.WFRTS && packet.type ==
    // PacketType.RTS) {
    // // Step 12
    // Packet ctsPacket = new Packet(PacketType.CTS, this.coordinate, this.id,
    // packet.originID, new HashSet<>(),
    // this.local_backoff.getOrDefault(packet.originID, 0),
    // this.remote_backoff.getOrDefault(packet.originID, 0),
    // this.exchange_seq_number.getOrDefault(packet.originID, 0));
    // this.current_state = state.WFDS;
    // setTimer(this, 200);
    // } else if (this.current_state == state.IDLE && packet.type == PacketType.RRTS
    // && Objects.equals(packet.destination, this.id)) {
    // // Step 13
    // reassignBackoffs(packet);
    // Packet rtsPacket = new Packet(PacketType.RTS, this.coordinate, this.id,
    // packet.originID, new HashSet<>(), this.my_backoff,
    // this.remote_backoff.getOrDefault(packet.originID, 0),
    // this.exchange_seq_number.getOrDefault(packet.originID, 0));
    // this.macawSend(rtsPacket, this);
    // // this.current_state = state.WFCTS;
    // // setTimer(this, 200);
    // } else if (packet.type == PacketType.RTS &&
    // !Objects.equals(packet.destination, this.id)) {
    // // Defer rule 1
    // this.current_state = state.QUIET;
    // setTimer(this, 500);
    // // TODO set a timer sufficient for A to hear B's CTS
    // } else if (packet.type == PacketType.DS &&
    // !Objects.equals(packet.destination, this.id)) {
    // // Defer rule 2
    // this.current_state = state.QUIET;
    // this.remote_backoff.put(packet.originID, packet.localBackoff);
    // this.remote_backoff.put(packet.destination, packet.remoteBackoff);
    // this.my_backoff = packet.localBackoff;
    // setTimer(this, 500);
    // // TODO set a timer sufficient for A to transmit data and hear B's ack
    // } else if (packet.type == PacketType.CTS &&
    // !Objects.equals(packet.destination, this.id)) {
    // // Defer rule 3
    // this.remote_backoff.put(packet.originID, packet.localBackoff);
    // this.remote_backoff.put(packet.destination, packet.remoteBackoff);
    // this.current_state = state.QUIET;
    // this.my_backoff = packet.localBackoff;
    // setTimer(this, 500);
    // // TODO set a timer suffecient for B to hear A's data.
    // } else if (packet.type == PacketType.RRTS &&
    // !Objects.equals(packet.destination, this.id)) {
    // // Defer rule 4
    // this.remote_backoff.put(packet.originID, packet.localBackoff);
    // this.remote_backoff.put(packet.destination, packet.remoteBackoff);
    // this.current_state = state.QUIET;
    // this.my_backoff = packet.localBackoff;
    // setTimer(this, 500);
    // // TODO set a timer sufficient for an RTS-CTS exchange.
    // }
    // return Optional.empty();
    // }

    // void reassignBackoffs(Packet packet) {
    // if (packet.sequenceNumber >
    // this.exchange_seq_number.getOrDefault(packet.originID, 0)) {
    // this.local_backoff.put(packet.originID, packet.remoteBackoff);
    // this.remote_backoff.put(packet.originID, packet.localBackoff);

    // Integer previousSeq = this.exchange_seq_number.getOrDefault(packet.originID,
    // 0);
    // this.exchange_seq_number.put(packet.originID, previousSeq + 1);
    // this.retry_count.put(packet.originID, 1);
    // } else {
    // // Packet is a retransmission
    // this.local_backoff.put(packet.originID, packet.localBackoff +
    // packet.remoteBackoff - this.remote_backoff.getOrDefault(packet.originID, 0));
    // }
    // }

    // // When a node wants to send something, it should call this method instead of
    // directly calling the Network
    // // send method. This has to do with the MACAW implementation.
    // void macawSend(Packet packet, Node node) {
    // // Step 1 from paper
    // if (this.current_state == state.IDLE) {
    // this.current_state = state.CONTEND;
    // setTimer(node, 50);
    // } else {
    // network.send(packet);
    // }
    // }

    // void setTimer(Node node, long duration) {
    // if (task != null) {
    // task.cancel();
    // }
    // task = new TimerTask() {
    // @Override
    // public void run() {
    // if (node.current_state == state.WFCntend) {
    // // first timeout rule
    // node.setTimer(node, 200);
    // node.current_state = state.CONTEND;
    // } else if (node.current_state == state.CONTEND) {
    // // second timeout rule
    // // TODO this part is why C does not go back to IDLE.
    // if (node.senderInitiated) {
    // Packet rtsPacket = new Packet(PacketType.RTS, node.coordinate, node.id,
    // node.communicatingWith, new HashSet<>(),
    // node.local_backoff.getOrDefault(node.communicatingWith, 0),
    // node.remote_backoff.getOrDefault(node.communicatingWith, 0),
    // node.exchange_seq_number.getOrDefault(node.communicatingWith, 0));
    // node.macawSend(rtsPacket, node);
    // node.current_state = state.WFCTS;
    // node.setTimer(node, 200);
    // } else if (node.receiverInitiated) {
    // Packet rrtsPacket = new Packet(PacketType.RRTS, node.coordinate, node.id,
    // node.communicatingWith, new HashSet<>(),
    // node.local_backoff.getOrDefault(node.communicatingWith, 0),
    // node.remote_backoff.getOrDefault(node.communicatingWith, 0),
    // node.exchange_seq_number.getOrDefault(node.communicatingWith, 0));
    // network.send(rrtsPacket);
    // // NOTE: the two papers do not correspond here. According to the army paper,
    // the state should be IDLE.
    // // According to the other paper, the state should be WFDS. Idle makes more
    // sense.
    // node.current_state = state.IDLE;
    // setTimer(node, 200);
    // }
    // } else {
    // node.current_state = state.IDLE;
    // }
    // }
    // };

    // Timer timer = new Timer();
    // // TODO Random timer, range might not make sense.
    // timer.schedule(task, ThreadLocalRandom.current().nextInt((int) duration,
    // (int) duration + 1));
    // }

}