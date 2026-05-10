package client;

import remote.ILobbyService;

import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.Scanner;

public class Client {

    private static boolean isUsernameTaken(RemoteException e) {
        String msg = e.getMessage();
        return msg != null && msg.contains("Username already in use");
    }

    public static void main(String[] args) {
        System.setProperty("java.rmi.server.hostname", "127.0.0.1");

        Scanner x = new Scanner(System.in);

        try {
            ClientCallbackImpl callback = new ClientCallbackImpl();

            ILobbyService lobby = (ILobbyService) Naming.lookup("rmi://127.0.0.1:6666/lobby");
            System.out.println(lobby.ping());

            String userName;
            while (true) {
                System.out.println("Enter your username (must be unique while online):");
                userName = x.nextLine();
                if (userName == null || userName.isBlank()) {
                    System.out.println("Empty name — try again.");
                    continue;
                }
                userName = userName.trim();

                try {
                    lobby.register(userName, callback);
                    break;
                } catch (RemoteException e) {
                    if (isUsernameTaken(e)) {
                        System.out.println("That name is already taken. Pick another.");
                        continue;
                    }
                    throw e;
                }
            }

            System.out.println("Registered as \"" + userName
                    + "\". Commands: list, invite <name>, accept, decline, quit");

            final ILobbyService lobbyRef = lobby;
            final String nameRef = userName;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    lobbyRef.unregister(nameRef);
                } catch (RemoteException ignored) {
                    // server may already be down
                }
            }));

            while (true) {
                String line = x.nextLine();
                if (line == null) {
                    break;
                }
                line = line.trim();
                if (line.equalsIgnoreCase("quit")) {
                    lobby.unregister(userName);
                    break;
                }
                if (line.equalsIgnoreCase("list")) {
                    System.out.println(lobby.listPlayers());
                    continue;
                }
                if (line.equalsIgnoreCase("accept")) {
                    try {
                        lobby.acceptInvite(userName);
                        callback.clearPendingInvites();
                    } catch (RemoteException e) {
                        System.out.println("Accept failed: " + e.getMessage());
                    }
                    continue;
                }
                if (line.equalsIgnoreCase("decline")) {
                    try {
                        lobby.declineInvite(userName);
                        callback.clearPendingInvites();
                    } catch (RemoteException e) {
                        System.out.println("Decline failed: " + e.getMessage());
                    }
                    continue;
                }
                if (line.toLowerCase().startsWith("invite ")) {
                    String target = line.substring(7).trim();
                    if (target.isEmpty()) {
                        System.out.println("Usage: invite <username>");
                        continue;
                    }
                    if (target.equalsIgnoreCase(userName)) {
                        System.out.println("You cannot invite yourself.");
                        continue;
                    }
                    try {
                        lobby.sendInvite(userName, target);
                        System.out.println("Invite sent to " + target);
                    } catch (RemoteException e) {
                        System.out.println("Invite failed: " + e.getMessage());
                    }
                    continue;
                }
                System.out.println("Unknown command. Use: list, invite <name>, accept, decline, quit");
            }

        } catch (MalformedURLException e) {
            System.err.println("Bad RMI URL: " + e.getMessage());
        } catch (NotBoundException e) {
            System.err.println("Nothing bound at that name — is the server running? " + e.getMessage());
        } catch (RemoteException e) {
            System.err.println("RMI error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
