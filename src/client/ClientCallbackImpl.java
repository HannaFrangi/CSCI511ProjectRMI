package client;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class ClientCallbackImpl extends UnicastRemoteObject implements IClientCallback {


    private final AtomicReference<String> PendingInvites = new AtomicReference<>();

    public String getPendingInvites() throws RemoteException {
      return  PendingInvites.get();
    }

    public void clearPendingInvites() throws RemoteException {
        PendingInvites.setRelease(null);
    }


    public ClientCallbackImpl() throws RemoteException {
        super();
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
        PendingInvites.set(fromUsername);
        System.out.println("[game] invite received from " + fromUsername);
    }


    @Override
    public List<Integer> getStats() throws RemoteException {
        return Collections.emptyList();
    }

}
