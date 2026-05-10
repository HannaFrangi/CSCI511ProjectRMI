package client;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class ClientCallbackImpl extends UnicastRemoteObject implements IClientCallback {

    private final AtomicReference<String> pendingInviteFrom = new AtomicReference<>();

    public ClientCallbackImpl() throws RemoteException {
        super();
    }

    public String getPendingInviteFrom() {
        return pendingInviteFrom.get();
    }

    public void clearPendingInviteFrom() {
        pendingInviteFrom.set(null);
    }

    @Override
    public void onLobbyUpdate(String message) throws RemoteException {
        System.out.println("[lobby] " + message);
    }

    @Override
    public void onMatchFinish() throws RemoteException {
        System.out.println("[game] match finished");
    }

    @Override
    public void onOpponentDisconnect() throws RemoteException {
        System.out.println("[game] opponent disconnected");
    }

    @Override
    public void onInviteReceived(String fromUsername) throws RemoteException {
        pendingInviteFrom.set(fromUsername);
        System.out.println("[invite] from " + fromUsername + " — type accept or decline");
    }

    @Override
    public List<Integer> getStats() throws RemoteException {
        return Collections.emptyList();
    }
}
