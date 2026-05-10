package model;

import client.IClientCallback;

public class PlayerEntry {

    private final IClientCallback clientCallback;
    private PlayerStatusModel status;

    public PlayerEntry(IClientCallback clientCallback, PlayerStatusModel status) {
        this.clientCallback = clientCallback;
        this.status = status;
    }

    public IClientCallback getClientCallback() {
        return clientCallback;
    }

    public PlayerStatusModel getStatus() {
        return status;
    }

    public void setStatus(PlayerStatusModel status) {
        this.status = status;
    }

}
