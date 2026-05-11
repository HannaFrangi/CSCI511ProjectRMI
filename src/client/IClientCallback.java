package client;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface IClientCallback extends Remote {
    void onLobbyUpdate(String message) throws RemoteException;

    void onMatchFinish() throws RemoteException;

    void onOpponentDisconnect() throws RemoteException;

    void onInviteReceived(String fromUsername) throws RemoteException;

    void onGameState(String state) throws RemoteException;

    List<Integer> getStats() throws RemoteException;
}
