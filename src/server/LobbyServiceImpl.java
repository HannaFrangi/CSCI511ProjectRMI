package server;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import client.IClientCallback;
import model.PlayerEntry;
import model.PlayerStatusModel;
import remote.ILobbyService;

public class LobbyServiceImpl extends UnicastRemoteObject implements ILobbyService {

    private final ConcurrentHashMap<String, PlayerEntry> userList = new ConcurrentHashMap<>();
    /** Key = invitee, value = inviter */
    private final ConcurrentHashMap<String, String> pendingInvites = new ConcurrentHashMap<>();
    private final Object inviteLock = new Object();

    protected LobbyServiceImpl() throws RemoteException {
        super();
    }

    @Override
    public String ping() throws RemoteException {
        return "Server is Running ";
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
        System.out.println("Registered: " + username + " (" + userList.size() + " online)");

        notifyQuiet(callback, "Welcome, " + username + ". You are online.");
    }

    @Override
    public void unregister(String userName) throws RemoteException {
        PlayerEntry removed = userList.remove(userName);
        if (removed == null) {
            return;
        }
        synchronized (inviteLock) {
            pendingInvites.remove(userName);
            Iterator<Map.Entry<String, String>> it = pendingInvites.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, String> e = it.next();
                if (e.getValue().equals(userName)) {
                    it.remove();
                }
            }
        }
        System.out.println("Unregistered: " + userName + " (" + userList.size() + " online)");
        for (PlayerEntry peer : userList.values()) {
            notifyQuiet(peer.getClientCallback(), userName + " left the lobby");
        }
    }

    @Override
    public void sendInvite(String fromUsername, String toUsername) throws RemoteException {
        IClientCallback callback;
        synchronized (inviteLock) {

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
                throw new RemoteException(toUsername + " is busy try again");
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
            synchronized (inviteLock) {
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

        synchronized (inviteLock) {
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
        }

        System.out.println("Match starting: " + fromUsername + " vs " + inviteeUsername);
        notifyQuiet(inviterEntry.getClientCallback(),
                "Invite accepted by " + inviteeUsername + ". Game starting (stub).");
        notifyQuiet(inviteeEntry.getClientCallback(),
                "You accepted. Playing with " + fromUsername + " (stub).");
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

        synchronized (inviteLock) {
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

    private static void notifyQuiet(IClientCallback callback, String message) {
        try {
            callback.onLobbyUpdate(message);
        } catch (RemoteException e) {
            System.err.println("Callback failed: " + e.getMessage());
        }
    }
}
