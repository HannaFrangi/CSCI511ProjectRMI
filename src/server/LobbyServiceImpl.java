package server;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import client.IClientCallback;
import model.PlayerEntry;
import model.PlayerStatusModel;
import model.Xo;
import remote.ILobbyService;

public class LobbyServiceImpl extends UnicastRemoteObject implements ILobbyService {

    private static final long HEARTBEAT_TIMEOUT_MS = 45_000L;
    private static final long HEARTBEAT_CHECK_MS = 15_000L;
    private final ConcurrentHashMap<String, PlayerEntry> userList = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> lastHeartbeat = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> pendingInvites = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> matchPartner = new ConcurrentHashMap<>();
    private final Object lobbyLock = new Object();
    private final ScheduledExecutorService heartbeatWatchdog;
    private final ConcurrentHashMap<String, Xo> gameByPlayer = new ConcurrentHashMap<>();
    private final ScoreStore scoreStore = new ScoreStore("scores.csv");

    protected LobbyServiceImpl() throws RemoteException {
        super();
        scoreStore.load();
        heartbeatWatchdog = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "lobby-heartbeat");
            t.setDaemon(true);
            return t;
        });
        heartbeatWatchdog.scheduleAtFixedRate(this::evictStaleUsers, HEARTBEAT_CHECK_MS,
                HEARTBEAT_CHECK_MS, TimeUnit.MILLISECONDS);
    }

    private void evictStaleUsers() {
        try {
            long now = System.currentTimeMillis();
            List<String> users = new ArrayList<>(userList.keySet());
            for (String u : users) {
                Long t = lastHeartbeat.get(u);
                if (t == null || now - t > HEARTBEAT_TIMEOUT_MS) {
                    System.out.println("Heartbeat timeout, removing: " + u);
                    removeConnectedUser(u, true);
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    @Override
    public String ping() throws RemoteException {
        return "Server is Running ";
    }

    @Override
    public void heartbeat(String userName) throws RemoteException {
        if (userName == null || userName.isBlank()) {
            return;
        }
        userName = userName.trim();
        if (!userList.containsKey(userName)) {
            return;
        }
        lastHeartbeat.put(userName, System.currentTimeMillis());
    }

    @Override
    public void register(String username, IClientCallback callback) throws RemoteException {
        if (userList.containsKey(username)) {
            throw new RemoteException("Username already in use: " + username);
        }

        for (PlayerEntry peer : userList.values()) {
            notifyQuiet(peer.getClientCallback(), username + " joined the lobby");
        }

        userList.put(username, new PlayerEntry(callback, PlayerStatusModel.ONLINE));
        lastHeartbeat.put(username, System.currentTimeMillis());
        System.out.println("Registered: " + username + " (" + userList.size() + " online)");

        notifyQuiet(callback, "Welcome, " + username + ". You are online.");
    }

    @Override
    public void unregister(String userName) throws RemoteException {
        if (userName == null || userName.isEmpty()) {
            throw new RemoteException("Username Cannot be empty");
        }
        removeConnectedUser(userName.trim(), false);
    }

    private void removeConnectedUser(String userName, boolean timeout) {
        if (userName == null || userName.isBlank()) {
            return;
        }
        userName = userName.trim();

        String partner;
        synchronized (lobbyLock) {
            partner = matchPartner.remove(userName);
            if (partner != null) {
                matchPartner.remove(partner);
                gameByPlayer.remove(userName);
                gameByPlayer.remove(partner);
            }
            pendingInvites.remove(userName);
            Iterator<Map.Entry<String, String>> it = pendingInvites.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, String> e = it.next();
                if (e.getValue().equals(userName)) {
                    it.remove();
                }
            }
        }

        PlayerEntry removed = userList.remove(userName);
        lastHeartbeat.remove(userName);
        if (removed == null) {
            return;
        }

        if (partner != null) {
            PlayerEntry peerEntry = userList.get(partner);
            if (peerEntry != null) {
                peerEntry.setStatus(PlayerStatusModel.ONLINE);
                String msg = timeout
                        ? "Your opponent timed out. You are ONLINE again."
                        : "Your opponent disconnected. You are ONLINE again.";
                notifyQuiet(peerEntry.getClientCallback(), msg);
                try {
                    peerEntry.getClientCallback().onOpponentDisconnect();
                } catch (RemoteException e) {
                    System.err.println("onOpponentDisconnect failed: " + e.getMessage());
                }
            }
        }

        String reason = timeout ? " (timed out)" : "";
        System.out.println("Unregistered: " + userName + reason + " (" + userList.size() + " online)");
        for (PlayerEntry peer : userList.values()) {
            notifyQuiet(peer.getClientCallback(), userName + " left the lobby");
        }
    }

    @Override
    public void sendInvite(String fromUsername, String toUsername) throws RemoteException {
        IClientCallback callback;
        synchronized (lobbyLock) {

            if (fromUsername == null || toUsername == null) {
                throw new RemoteException("Usernames cannot be null");
            }
            fromUsername = fromUsername.trim();
            toUsername = toUsername.trim();

            if (fromUsername.isEmpty() || toUsername.isEmpty()) {
                throw new RemoteException("Usernames cannot be empty");
            }
            if (fromUsername.equalsIgnoreCase(toUsername)) {
                throw new RemoteException("You cannot invite yourself");
            }

            PlayerEntry p1 = userList.get(fromUsername);
            PlayerEntry p2 = userList.get(toUsername);

            if (p1 == null) {
                throw new RemoteException(fromUsername + " is not registered");
            }

            if (p2 == null) {
                throw new RemoteException(toUsername + " is not registered");
            }

            if (p1.getStatus() != PlayerStatusModel.ONLINE) {
                throw new RemoteException("You are busy and cannot send invites");
            }

            if (p2.getStatus() != PlayerStatusModel.ONLINE) {
                throw new RemoteException(toUsername + " is busy, try again");
            }

            if (pendingInvites.containsKey(toUsername)) {
                throw new RemoteException(toUsername + " already has a pending invite");
            }
            pendingInvites.put(toUsername, fromUsername);
            callback = p2.getClientCallback();
        }

        try {
            callback.onInviteReceived(fromUsername);
            System.out.println("Invite sent: " + fromUsername + " -> " + toUsername);
        } catch (RemoteException e) {
            synchronized (lobbyLock) {
                String current = pendingInvites.get(toUsername);
                if (fromUsername.equals(current)) {
                    pendingInvites.remove(toUsername);
                }
            }
            throw new RemoteException("Failed to deliver invite to " + toUsername, e);
        }
    }

    @Override
    public void acceptInvite(String inviteeUsername) throws RemoteException {
        if (inviteeUsername == null || inviteeUsername.isBlank()) {
            throw new RemoteException("Username cannot be empty");
        }
        inviteeUsername = inviteeUsername.trim();

        String fromUsername;
        PlayerEntry inviterEntry;
        PlayerEntry inviteeEntry;
        Xo game;

        synchronized (lobbyLock) {
            fromUsername = pendingInvites.get(inviteeUsername);
            if (fromUsername == null) {
                throw new RemoteException("No pending invite for " + inviteeUsername);
            }

            inviterEntry = userList.get(fromUsername);
            inviteeEntry = userList.get(inviteeUsername);

            if (inviterEntry == null || inviteeEntry == null) {
                throw new RemoteException("Inviter or invitee no longer online");
            }
            if (inviterEntry.getStatus() != PlayerStatusModel.ONLINE
                    || inviteeEntry.getStatus() != PlayerStatusModel.ONLINE) {
                throw new RemoteException("Players must be ONLINE to accept");
            }

            pendingInvites.remove(inviteeUsername);

            inviterEntry.setStatus(PlayerStatusModel.BUSY);
            inviteeEntry.setStatus(PlayerStatusModel.BUSY);

            matchPartner.put(fromUsername, inviteeUsername);
            matchPartner.put(inviteeUsername, fromUsername);

            game = new Xo(fromUsername, inviteeUsername);
            gameByPlayer.put(fromUsername, game);
            gameByPlayer.put(inviteeUsername, game);
        }

        System.out.println("Match starting: " + fromUsername + " vs " + inviteeUsername);
        boolean okInviter = notifyLobby(inviterEntry.getClientCallback(),
                "Invite accepted by " + inviteeUsername + ". Game starting.");
        boolean okInvitee = notifyLobby(inviteeEntry.getClientCallback(),
                "You accepted. Playing with " + fromUsername + ".");

        if (!okInviter || !okInvitee) {
            System.err.println("Post-accept callback failed; abandoning match.");
            abandonStubMatch(fromUsername, inviteeUsername);
        } else {
            pushGameStateBoth(game);
        }
    }

    @Override
    public void declineInvite(String inviteeUsername) throws RemoteException {
        if (inviteeUsername == null || inviteeUsername.isBlank()) {
            throw new RemoteException("Username cannot be empty");
        }

        inviteeUsername = inviteeUsername.trim();
        String fromUsername;
        PlayerEntry inviter;
        PlayerEntry invitee;

        synchronized (lobbyLock) {
            fromUsername = pendingInvites.get(inviteeUsername);
            if (fromUsername == null) {
                throw new RemoteException("No pending invite for " + inviteeUsername);
            }

            inviter = userList.get(fromUsername);
            invitee = userList.get(inviteeUsername);

            if (inviter == null || invitee == null) {
                pendingInvites.remove(inviteeUsername);
                throw new RemoteException("Inviter or invitee no longer online");
            }
            if (inviter.getStatus() != PlayerStatusModel.ONLINE
                    || invitee.getStatus() != PlayerStatusModel.ONLINE) {
                throw new RemoteException("Players must be ONLINE to decline");
            }

            pendingInvites.remove(inviteeUsername);
        }

        notifyQuiet(inviter.getClientCallback(),
                "Invite declined by " + inviteeUsername + ".");
        notifyQuiet(invitee.getClientCallback(),
                "You declined the invite.");
    }

    @Override
    public void leaveMatch(String username) throws RemoteException {
        if (username == null || username.isBlank()) {
            throw new RemoteException("Username cannot be empty");
        }
        username = username.trim();

        String partner;
        PlayerEntry selfEntry;
        PlayerEntry partnerEntry;

        synchronized (lobbyLock) {
            partner = matchPartner.remove(username);
            if (partner == null) {
                throw new RemoteException("You are not in a match");
            }
            matchPartner.remove(partner);
            gameByPlayer.remove(username);
            gameByPlayer.remove(partner);

            selfEntry = userList.get(username);
            partnerEntry = userList.get(partner);

            if (selfEntry != null) {
                selfEntry.setStatus(PlayerStatusModel.ONLINE);
            }
            if (partnerEntry != null) {
                partnerEntry.setStatus(PlayerStatusModel.ONLINE);
            }
        }

        if (selfEntry != null) {
            notifyQuiet(selfEntry.getClientCallback(), "You left the match. You are ONLINE.");
        }
        if (partnerEntry != null) {
            notifyLobby(partnerEntry.getClientCallback(),
                    username + " left the match. You are ONLINE again.");
            try {
                partnerEntry.getClientCallback().onOpponentDisconnect();
            } catch (RemoteException e) {
                System.err.println("onOpponentDisconnect failed: " + e.getMessage());
            }
        }
    }

    @Override
    public void makeMove(String playerName, String coords) throws RemoteException {
        if (playerName == null) {
            throw new RemoteException("Player name cannot be null");
        }
        if (coords == null) {
            throw new RemoteException("Coords cannot be null");
        }
        String player = playerName.trim();
        if (player.isEmpty()) {
            throw new RemoteException("Player name cannot be empty");
        }
        String coordTrim = coords.trim();
        if (coordTrim.isEmpty()) {
            throw new RemoteException("Coords cannot be empty");
        }

        String moveResult;
        Xo game;
        PlayerEntry entryX;
        PlayerEntry entryO;

        synchronized (lobbyLock) {
            game = gameByPlayer.get(player);
            if (game == null) {
                throw new RemoteException("Not in a game");
            }
            try {
                moveResult = game.applyMove(player, coordTrim);
            } catch (IllegalArgumentException | IllegalStateException e) {
                throw new RemoteException(e.getMessage());
            }

            entryX = userList.get(game.getPlayerX());
            entryO = userList.get(game.getPlayerO());
            if (entryX == null || entryO == null) {
                throw new RemoteException("Opponent no longer registered");
            }

            if (moveResult != null) {
                finishMatchAfterGame(game.getPlayerX(), game.getPlayerO());
            }
        }

        String snapX = game.formatSnapshot(game.getPlayerX());
        String snapO = game.formatSnapshot(game.getPlayerO());
        notifyGameStateQuiet(entryX.getClientCallback(), snapX);
        notifyGameStateQuiet(entryO.getClientCallback(), snapO);

        if (moveResult != null) {
            scoreStore.recordGameEnd(moveResult, game.getPlayerX(), game.getPlayerO());
            notifyMatchFinishQuiet(entryX.getClientCallback());
            notifyMatchFinishQuiet(entryO.getClientCallback());
        }
    }

    @Override
    public List<String> listPlayers() throws RemoteException {
        List<String> keys = new ArrayList<>(userList.keySet());
        Collections.sort(keys);
        List<String> x = new ArrayList<>();
        for (String name : keys) {
            PlayerEntry entry = userList.get(name);
            x.add(name + " (" + entry.getStatus() + ")");
        }
        System.out.println("listPlayers -> " + x);
        return x;
    }

    @Override
    public List<String> getLeaderboard() throws RemoteException {
        return scoreStore.getLeaderboardLines();
    }

    /** Must be called with {@code lobbyLock} held. */
    private void finishMatchAfterGame(String nameX, String nameO) {
        matchPartner.remove(nameX);
        matchPartner.remove(nameO);
        gameByPlayer.remove(nameX);
        gameByPlayer.remove(nameO);
        PlayerEntry ex = userList.get(nameX);
        PlayerEntry eo = userList.get(nameO);
        if (ex != null) {
            ex.setStatus(PlayerStatusModel.ONLINE);
        }
        if (eo != null) {
            eo.setStatus(PlayerStatusModel.ONLINE);
        }
        System.out.println("Game finished: " + nameX + " vs " + nameO);
    }

    private void abandonStubMatch(String a, String b) {
        synchronized (lobbyLock) {
            matchPartner.remove(a);
            matchPartner.remove(b);
            gameByPlayer.remove(a);
            gameByPlayer.remove(b);
            PlayerEntry ea = userList.get(a);
            PlayerEntry eb = userList.get(b);
            if (ea != null) {
                ea.setStatus(PlayerStatusModel.ONLINE);
            }
            if (eb != null) {
                eb.setStatus(PlayerStatusModel.ONLINE);
            }
        }
        System.out.println("Match abandoned: " + a + " vs " + b);
    }

    private void pushGameStateBoth(Xo game) {
        String nameX = game.getPlayerX();
        String nameO = game.getPlayerO();
        PlayerEntry ex = userList.get(nameX);
        PlayerEntry eo = userList.get(nameO);
        if (ex == null || eo == null) {
            return;
        }
        notifyGameStateQuiet(ex.getClientCallback(), game.formatSnapshot(nameX));
        notifyGameStateQuiet(eo.getClientCallback(), game.formatSnapshot(nameO));
    }

    private static void notifyGameStateQuiet(IClientCallback callback, String snapshot) {
        if (callback == null) {
            return;
        }
        try {
            callback.onGameState(snapshot);
        } catch (RemoteException e) {
            System.err.println("onGameState failed: " + e.getMessage());
        }
    }

    private static void notifyMatchFinishQuiet(IClientCallback callback) {
        if (callback == null) {
            return;
        }
        try {
            callback.onMatchFinish();
        } catch (RemoteException e) {
            System.err.println("onMatchFinish failed: " + e.getMessage());
        }
    }

    private static boolean notifyLobby(IClientCallback callback, String message) {
        try {
            callback.onLobbyUpdate(message);
            return true;
        } catch (RemoteException e) {
            System.err.println("Callback failed: " + e.getMessage());
            return false;
        }
    }

    private static void notifyQuiet(IClientCallback callback, String message) {
        notifyLobby(callback, message);
    }
}
