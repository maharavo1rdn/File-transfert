package Serveur;

import java.io.*;
import java.net.*;
import java.util.Map;

public class SlaveServer {
    private int port; // Champ de classe pour le port
    private int partNumber;
    // private final static String addressBroadCast = "192.168.35.255";

    public SlaveServer(int port, int partNumber) {
        this.port = port;
        this.partNumber = partNumber;
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Usage: java SlaveServer <port>");
            return;
        }
        String configFile = "config/configSlave.txt"; // Chemin du fichier de configuration

        Map<String, String> config = ConfigReader.readConfig(configFile);
        String[] slavePortStrings = config.get("slavePorts").split(",");
        int[] slavePorts = new int[slavePortStrings.length];

        for (int i = 0; i < slavePortStrings.length; i++) {
            slavePorts[i] = Integer.parseInt(slavePortStrings[i].trim());
        }

        int port = Integer.parseInt(args[0]);
        int partNumber = Integer.parseInt(args[1]);
        String addressBroadCast = config.get("adresseBroadCast");
        SlaveServer server = new SlaveServer(port, partNumber);
        server.start(slavePorts, addressBroadCast);
    }

    public void start(int[] allPorts, String broadcastAddress) {
        sendBroadcast(broadcastAddress, 12349);
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Sous-serveur démarré sur le port " + port);

            while (true) {
                try (Socket clientSocket = serverSocket.accept();
                        DataInputStream in = new DataInputStream(clientSocket.getInputStream());
                        DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream())) {

                    String command = in.readUTF();

                    if ("STORE_PART".equals(command)) {
                        handleStorePart(in, allPorts);
                    } else if ("GET_PART".equals(command)) {
                        handleGetPart(in, out);
                    } else if ("DELETE_PART".equals(command)) {
                        handleDeletePart(in, out);
                    } else {
                        System.err.println("Commande inconnue reçue : " + command);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Erreur dans le sous-serveur sur le port " + port + " : " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void sendBroadcast(String broadcastAddress, int broadcastPort) {
        try (DatagramSocket socket = new DatagramSocket()) {
            InetAddress localAddress = InetAddress.getLocalHost();
            String ipAddress = localAddress.getHostAddress(); // Récupère l'adresse IP sous forme de chaîne

            // Créer le message à envoyer
            String message = "SlaveServer available on IP " + ipAddress + " port " + port + " with partition "
                    + partNumber;
            InetAddress broadcastInetAddress = InetAddress.getByName(broadcastAddress);
            DatagramPacket packet = new DatagramPacket(message.getBytes(), message.length(),
                    broadcastInetAddress, broadcastPort);

            // Envoyer le message en broadcast
            socket.send(packet);
            System.out.println("Message de broadcast envoyé : " + message);
        } catch (IOException e) {
            System.err.println("Erreur lors de l'envoi du message de broadcast : " + e.getMessage());
        }
    }

    private void handleStorePart(DataInputStream in, int[] allPorts) throws IOException {
        String fileName = in.readUTF();
        boolean isReplication = in.readBoolean(); // Lire si c'est une réplication
        long fileSize = in.readLong(); // Lire la taille exacte de la partie
        System.out.println("Réception du fichier : " + fileName + " (" + fileSize + " octets)");

        File file = new File("storage" + partNumber + "/" + fileName);
        file.getParentFile().mkdirs();

        long totalBytesRead = 0;
        try (FileOutputStream fileOut = new FileOutputStream(file)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while (totalBytesRead < fileSize && (bytesRead = in.read(buffer, 0,
                    (int) Math.min(buffer.length, fileSize - totalBytesRead))) != -1) {
                fileOut.write(buffer, 0, bytesRead);
                totalBytesRead += bytesRead;
            }
        }

        System.out.println("Fichier reçu avec succès : " + fileName);

        if (!isReplication) {
            replicateToAnotherServer(fileName, file, allPorts);
        }
    }

    private void replicateToAnotherServer(String fileName, File file, int[] allPorts) {
        int replicaPort;

        // Choisir un port différent pour la réplication
        do {
            replicaPort = allPorts[(int) (Math.random() * allPorts.length)];
        } while (replicaPort == this.port);

        try (Socket socket = new Socket("localhost", replicaPort);
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                FileInputStream fileIn = new FileInputStream(file)) {

            System.out.println("Connexion établie avec le sous-serveur sur le port : " + replicaPort);

            // Envoyer le type d'action et les informations sur le fichier
            out.writeUTF("STORE_PART");
            out.writeUTF(fileName);
            out.writeBoolean(true); // Indique que c'est une réplication

            // Envoyer la taille du fichier
            long fileSize = file.length();
            out.writeLong(fileSize);

            // Lire et envoyer les données du fichier
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fileIn.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }

            System.out.println("Fichier répliqué avec succès vers le sous-serveur " + replicaPort);

        } catch (IOException e) {
            System.err.println(
                    "Erreur lors de la réplication vers le sous-serveur " + replicaPort + " : " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleGetPart(DataInputStream in, DataOutputStream out) throws IOException {
        String fileName = in.readUTF();
        File file = new File("storage" + partNumber + "/" + fileName);

        if (!file.exists()) {
            out.writeUTF("NOT_FOUND");
            System.err.println("Partie demandée introuvable : " + fileName);
            return;
        }

        out.writeUTF("FOUND");

        try (BufferedInputStream fileIn = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fileIn.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }

        System.out.println("Partie envoyée : " + file.getAbsolutePath());
    }

    private void handleDeletePart(DataInputStream in, DataOutputStream out) throws IOException {
        String fileName = in.readUTF();
        File file = new File("storage" + partNumber + "/" + fileName);

        if (file.exists()) {
            if (file.delete()) {
                out.writeUTF("SUCCESS");
                System.out.println("Fichier supprimé : " + file.getAbsolutePath());
            } else {
                out.writeUTF("FAILURE");
                System.err.println("Échec de la suppression du fichier : " + file.getAbsolutePath());
            }
        } else {
            out.writeUTF("NOT_FOUND");
            System.err.println("Fichier introuvable pour suppression : " + file.getAbsolutePath());
        }
    }
}
