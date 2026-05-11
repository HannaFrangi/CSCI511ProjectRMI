package remote;

import client.IClientCallback;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface ILobbyService extends Remote {

    String ping() throws RemoteException;

    void register(String userName, IClientCallback callback) throws RemoteException;

    void heartbeat(String userName) throws RemoteException;

    void unregister(String userName) throws RemoteException;

    void sendInvite(String fromUsername, String toUsername) throws RemoteException;

    void acceptInvite(String inviteeUsername) throws RemoteException;

    void declineInvite(String inviteeUsername) throws RemoteException;

    void leaveMatch(String username) throws RemoteException;

    void makeMove(String PlayerName , String coords) throws RemoteException;

    List<String> listPlayers() throws RemoteException;
}
