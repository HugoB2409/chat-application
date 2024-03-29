package Server;

import Common.ClientPacket;
import Common.ServerCommand;
import Common.ServerPacket;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class Connection extends Thread {
    private final ObjectOutputStream out;
    private final ObjectInputStream in;
    private final Server server;
    private final String clientIP;
    boolean quitChat = false;
    private Observer observer;
    private String username = "";

    public Connection(Socket socket, Server server) {
        clientIP = socket.getInetAddress().getHostAddress();
        this.server = server;
        try {
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void run() {
        new Thread(this::handle).start();
    }

    /**
     * envoie la liste des clients au client de cette connection
     */
    public void sendListClient() {
        try {
            out.writeObject(new ServerPacket(username, ServerCommand.LIST_CLIENTS, formatUsersList()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String getUsername() {
        return username;
    }

    /**
     * envoie le message d'un client au client de cette connection
     * @param sender
     * @param content
     */
    public void update(String sender, String content) {
        try {
            out.writeObject(new ServerPacket(sender, ServerCommand.MESSAGE, content));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Observer getObserver() {
        return this.observer;
    }

    public void setObserver(Observer observer) {
        this.observer = observer;
    }

    /**
     * fonction principale du thread de la connection
     */
    private void handle() {
        waitForConnection();
        observer.notifyUserList();
        handleChat();
    }

    /**
     * fonction qui attend le packet d'initialisation de connection de la part du client,
     * donc un paquet avec un nom d'utilisateur valide
     */
    private void waitForConnection() {
        ClientPacket clientPacket;
        boolean accepted = false;
        try {
            while (!accepted) {
                clientPacket = (ClientPacket) in.readObject();
                username = clientPacket.getName();
                if (usernameIsValid()) {
                    accepted = true;
                    sendListClient();
                } else {
                    out.writeObject(new ServerPacket(username, ServerCommand.ERROR, "username: " + username + " invalide"));
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * fonction qui traite les multiples paquets du client lorsque la connexion est établie
     */
    private void handleChat() {
        ClientPacket clientPacket;
        try {
            while (!quitChat && (clientPacket = (ClientPacket) in.readObject()) != null) {
                switch (clientPacket.getCommand()) {
                    case ALL_CLIENTS -> sendToAll(clientPacket);
                    case TO_CLIENT, TO_CLIENTS -> sendToSome(clientPacket);
                    case LIST_CLIENTS -> sendListClient();
                    case QUIT -> quit();
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * indique si le nom d'utilisateur est valide, donc non utilisé et non vide
     * @return
     */
    private boolean usernameIsValid() {
        return !username.equals("") && usernameIsAvailable();
    }

    private boolean usernameIsAvailable() {
        return !observer.getUsernames().contains(username);
    }

    /**
     * formate la liste des utilisateurs reçue par l'observer
     * @return
     */
    private String formatUsersList() {
        return String.join(", ", observer.getUsernames());
    }

    /**
     * envoie un message à tout les clients
     * @param clientPacket
     * @throws IOException
     */
    private void sendToAll(ClientPacket clientPacket) throws IOException {
        observer.notify(clientPacket.getName(), clientPacket.getContent());
        out.writeObject(new ServerPacket(username, ServerCommand.MESSAGE, clientPacket.getContent()));
    }

    /**
     * envoie un message à certains clients selon les noms spécifiés dans le paquet
     * @param clientPacket
     * @throws IOException
     */
    private void sendToSome(ClientPacket clientPacket) throws IOException {
        observer.notifySpecificClient(clientPacket.getName(), clientPacket.getContent(), clientPacket.getUsers());
        out.writeObject(new ServerPacket(username, ServerCommand.MESSAGE, clientPacket.getContent()));
    }

    /**
     * ferme la connexion donc retabli le nom d'utilisateur comme libre
     * envoie la liste des clients aux clients qui sont toujours connectés
     * se ferme au côté du serveur
     */
    private void quit() {
        observer.unsubscribe(username);
        observer.notifyUserList();
        quitChat = true;
        server.remove(this);
    }
}