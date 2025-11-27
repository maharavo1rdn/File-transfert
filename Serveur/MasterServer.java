package Serveur;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.*;

public class MasterServer {
    private static boolean misyException = false;

    public static void main(String[] args) {
        String configFile = "config/configMaster.txt"; // Chemin du fichier de configuration
        Map<String, String> config = ConfigReader.readConfig(configFile);
        int port = Integer.parseInt(config.get("port"));
        int listPort = Integer.parseInt(config.get("listPort"));
        int downloadPort = Integer.parseInt(config.get("downloadPort"));
        int removePort = Integer.parseInt(config.get("removePort"));
        ArrayList<String> finalSlaveIp = new ArrayList<>();
        ArrayList<Integer> finalSlavePort = new ArrayList<>();

        // if (config.get("slavePorts").split(",") != null) {

        // String[] slavePortStrings = config.get("slavePorts").split(",");
        // String[] slaveIp = config.get("slaveIP").split(",");
        // int[] slavePorts = new int[slavePortStrings.length];
        // for (int i = 0; i < slavePortStrings.length; i++) {
        // slavePorts[i] = Integer.parseInt(slavePortStrings[i].trim());
        // finalSlavePort.add(slavePorts[i]);
        // finalSlaveIp.add(slaveIp[i]);
        // }
        // }
        // System.out.println(finalSlavePort);
        // ty le tamin mbola static be tsy natao tanaty conf
        // int port = 12345; // Port principal pour recevoir le fichier du client
        // int listPort = 12346; // Port secondaire pour la liste des fichiers
        // int downloadPort = 12347; // Port pour les téléchargements
        // int[] slavePorts = {23456, 23457, 23458}; // Ports des sous-serveurs

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Serveur principal en attente sur le port " + port);

            new Thread(() -> listenForSlaves(12349, finalSlavePort, finalSlaveIp)).start();
            new Thread(() -> handleFileListRequests(listPort)).start();
            new Thread(() -> handleFileDownloadRequests(downloadPort, finalSlaveIp, finalSlavePort)).start();
            new Thread(() -> handleFileDeleteRequests(removePort, finalSlaveIp, finalSlavePort)).start();

            while (true) {
                try (Socket clientSocket = serverSocket.accept()) {
                    System.out.println("Client connecté : " + clientSocket.getInetAddress());

                    // Gérer la réception du fichier
                    handleFileReception(clientSocket, finalSlavePort, finalSlaveIp);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void listenForSlaves(int broadcastPort, ArrayList<Integer> slavePorts, ArrayList<String> slaveIp) {
        try (DatagramSocket socket = new DatagramSocket(broadcastPort, InetAddress.getByName("0.0.0.0"))) {
            socket.setBroadcast(true); // Permet l'écoute en broadcast
            byte[] buffer = new byte[1024];
            System.out.println("En attente de messages de broadcast sur le port " + broadcastPort);

            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet); // Attendre un message de broadcast
                String message = new String(packet.getData(), 0, packet.getLength());
                System.out.println("Message reçu : " + message);

                // Extraire des informations comme le port et le numéro de partition du message
                String[] parts = message.split(" ");
                String port = parts[6]; // Partie du message qui contient le port
                if (!slavePorts.contains(Integer.parseInt(port))) {
                    slavePorts.add(Integer.parseInt(port));
                }

                String ip = parts[4];
                slaveIp.add(ip);

                System.out.println(port + " this is the port");
                String partNumber = parts[9]; // Partie du message qui contient le numéro de partition
                System.out.println(partNumber + " this is the part");
                System.out.println("Serveur esclave trouvé sur le port : " + port + ", partition : " + partNumber);
            }
        } catch (IOException e) {
            System.err.println("Erreur lors de l'écoute du broadcast : " + e.getMessage());
        }
    }

    private static void handleFileDeleteRequests(int deletePort, ArrayList<String> slaveAdressIp,
            ArrayList<Integer> slavePorts) {
        try (ServerSocket deleteServerSocket = new ServerSocket(deletePort)) {
            System.out.println("Serveur de suppression en attente sur le port " + deletePort);

            while (true) {
                try (Socket clientSocket = deleteServerSocket.accept();
                        DataInputStream in = new DataInputStream(clientSocket.getInputStream());
                        DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream())) {

                    // Lire le nom du fichier à supprimer
                    String fileName = in.readUTF();
                    System.out.println("Demande de suppression pour : " + fileName);

                    boolean success = true;

                    // Envoyer des commandes aux sous-serveurs pour supprimer les parties
                    for (int i = 0; i < slavePorts.size(); i++) {
                        String partFileName = appendPartSuffix(fileName, i + 1);
                        if (!deletePartFromSlave(partFileName, slaveAdressIp.get(i), slavePorts.get(i))) {
                            success = false;
                            System.err.println("Échec de suppression de la partie : " + partFileName);
                        }
                    }

                    if (success) {
                        updateLogFileAfterDeletion(fileName);
                        out.writeUTF("SUCCESS");
                        System.out.println("Fichier " + fileName + " supprimé avec succès.");
                    } else {
                        out.writeUTF("FAILURE");
                        System.err.println("Échec de suppression du fichier complet : " + fileName);
                    }

                } catch (IOException e) {
                    System.err.println("Erreur lors de la suppression : " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("Erreur du serveur de suppression : " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void updateLogFileAfterDeletion(String fileName) {
        File logFile = new File("log/log.txt");
        File tempFile = new File("log/temp_log.txt");

        try (BufferedReader reader = new BufferedReader(new FileReader(logFile));
                BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile))) {

            String currentLine;
            while ((currentLine = reader.readLine()) != null) {
                if (!currentLine.trim().equals(fileName)) {
                    writer.write(currentLine);
                    writer.newLine();
                }
            }

        } catch (IOException e) {
            System.err.println("Erreur lors de la mise à jour du log.txt : " + e.getMessage());
            e.printStackTrace();
            return;
        }

        // Remplacer l'ancien log.txt par le nouveau fichier temporaire
        if (!logFile.delete()) {
            System.err.println("Impossible de supprimer l'ancien log.txt.");
            return;
        }

        if (!tempFile.renameTo(logFile)) {
            System.err.println("Impossible de renommer le fichier temporaire en log.txt.");
        } else {
            System.out.println("log.txt mis à jour avec succès.");
        }
    }

    private static boolean deletePartFromSlave(String partFileName, String slaveAdressIp, int slavePort) {
        try (Socket slaveSocket = new Socket(slaveAdressIp, slavePort);
                DataOutputStream out = new DataOutputStream(slaveSocket.getOutputStream());
                DataInputStream in = new DataInputStream(slaveSocket.getInputStream())) {

            out.writeUTF("DELETE_PART");
            out.writeUTF(partFileName);

            String response = in.readUTF();
            return "SUCCESS".equals(response);

        } catch (IOException e) {
            System.err.println("Erreur lors de la suppression de la partie sur le sous-serveur : " + e.getMessage());
            return false;
        }
    }

    private static void handleFileReception(Socket clientSocket, ArrayList<Integer> slavePorts,
            ArrayList<String> slaveIp)
            throws IOException {
        try (DataInputStream inputStream = new DataInputStream(clientSocket.getInputStream())) {
            String fileName = inputStream.readUTF();
            long totalFileSize = inputStream.readLong();
            System.out.println("Fichier reçu : " + fileName + " (" + totalFileSize + " octets)");

            byte[] buffer = new byte[8192];
            long partSize = totalFileSize / slavePorts.size();
            long remainder = totalFileSize % slavePorts.size();

            long[] partSizes = new long[slavePorts.size()];
            for (int i = 0; i < slavePorts.size(); i++) {
                partSizes[i] = partSize;
                if (i < remainder) {
                    partSizes[i] += 1;
                }
            }

            ExecutorService executorService = Executors.newFixedThreadPool(slavePorts.size());
            for (int i = 0; i < slavePorts.size(); i++) {
                long targetSize = partSizes[i];
                long bytesReceived = 0;

                while (bytesReceived < targetSize) {
                    int bytesToRead = (int) Math.min(buffer.length, targetSize - bytesReceived);
                    int bytesRead = inputStream.read(buffer, 0, bytesToRead);
                    if (bytesRead == -1)
                        break;

                    bytesReceived += bytesRead;

                    final byte[] partBuffer = new byte[bytesRead];
                    System.arraycopy(buffer, 0, partBuffer, 0, bytesRead);

                    int slavePort = slavePorts.get(i);
                    String slaveIP = slaveIp.get(i);
                    String partFileName = appendPartSuffix(fileName, i + 1);
                    executorService.submit(() -> {
                        try {
                            sendToSlave(partBuffer, bytesRead, slavePort, partFileName, slaveIP);
                        } catch (IOException e) {
                            misyException = true;
                            try {
                                throw e;
                                // TODO Auto-generated catch block
                                // e.printStackTrace();
                            } catch (IOException ex) {
                            }
                        }
                    });
                    if (bytesReceived != targetSize) {
                        System.err.println("Erreur : taille incorrecte envoyée à la partie " + (i + 1));
                    }

                }

                System.out.println("Partie " + (i + 1) + " envoyée (" + targetSize + " octets)");
            }

            executorService.shutdown();
            executorService.awaitTermination(60, TimeUnit.SECONDS);
            if (misyException) {
                return;
            } else {
                logFileTransfer(fileName);
                System.out.println("Fichier traité et délégué aux sous-serveurs avec succès !");

            }
        } catch (IOException | InterruptedException e) {
            System.err.println("Erreur lors de la réception ou de l'envoi des parties : " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void handleFileDownloadRequests(int downloadPort, ArrayList<String> slaveAdressIp,
            ArrayList<Integer> slavePorts) {
        try (ServerSocket downloadServerSocket = new ServerSocket(downloadPort)) {
            System.out.println("Serveur de téléchargement en attente sur le port " + downloadPort);

            while (true) {
                try (Socket clientSocket = downloadServerSocket.accept();
                        DataInputStream in = new DataInputStream(clientSocket.getInputStream());
                        DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream())) {

                    // Lire le nom du fichier demandé
                    String requestedFile = in.readUTF();
                    System.out.println("Demande de téléchargement pour : " + requestedFile);

                    // Récupérer et envoyer chaque partie directement au client
                    for (int i = 0; i < slavePorts.size(); i++) {
                        String partFileName = appendPartSuffix(requestedFile, i + 1);
                        byte[] partData = fetchPartFromSlave(partFileName, slaveAdressIp.get(i),
                                (int) slavePorts.get(i), slavePorts);

                        if (partData == null) {
                            out.writeUTF("NOT_FOUND"); // Informer le client en cas d'erreur
                            System.err.println("Partie introuvable : " + partFileName);
                            return;
                        }

                        out.write(partData); // Envoyer les données directement
                        System.out.println("Partie " + partFileName + " envoyée au client.");
                    }

                    out.flush();
                    System.out.println("Fichier complet combiné et envoyé au client directement.");

                } catch (IOException e) {
                    System.err.println("Erreur lors de l'envoi du fichier au client : " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("Erreur du serveur de téléchargement : " + e.getMessage());
        }
    }

    private static void sendToSlave(byte[] partBuffer, int bytesRead, int slavePort, String partFileName,
            String slaveIp) throws IOException {
        try (Socket socket = new Socket(slaveIp, slavePort);
                DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {

            // Envoyer la commande et les métadonnées
            out.writeUTF("STORE_PART");
            out.writeUTF(partFileName);
            out.writeBoolean(false); // Indique que ce n'est pas une réplication
            out.writeLong(bytesRead);

            // Envoyer les données de la partie
            out.write(partBuffer, 0, bytesRead);
            System.out.println("Partie " + partFileName + " envoyée au sous-serveur sur le port " + slavePort);
        }
    }

    private static byte[] fetchPartFromSlave(String partFileName, String slaveAdressIp, int slavePort,
            ArrayList<Integer> slavePorts) {
        byte[] data = tryFetchPart(partFileName, slaveAdressIp, slavePort);
        boolean nahita = false;
        if (data == null) {
            System.out.println("Tentative de récupération d'une copie répliquée pour : " + partFileName);
            for (int port : slavePorts) {
                if (port != slavePort) {
                    data = tryFetchPart(partFileName, slaveAdressIp, port);
                    if (data != null) {
                        System.out.println("Récupération réussie depuis la copie répliquée sur le port " + port);
                        restorePartToSlave(partFileName, slaveAdressIp, slavePort, data);
                        nahita = true;
                        break;
                    }
                    if (nahita) {
                        break;
                    }
                }
            }
        }
        return data;
    }

    private static void restorePartToSlave(String partFileName, String slaveAdressIp, int slavePort, byte[] data) {
        try (Socket slaveSocket = new Socket(slaveAdressIp, slavePort);
                DataOutputStream out = new DataOutputStream(slaveSocket.getOutputStream())) {

            out.writeUTF("STORE_PART");
            out.writeUTF(partFileName);
            out.writeBoolean(false); // Indiquer que ce n'est pas une réplication

            out.write(data);
            System.out.println("Partie restaurée avec succès sur le sous-serveur " + slavePort);
        } catch (IOException e) {
            System.err.println(
                    "Erreur lors de la restauration de la partie " + partFileName + " sur le port " + slavePort);
        }
    }

    private static byte[] tryFetchPart(String partFileName, String slaveAdressIp, int slavePort) {
        try (Socket slaveSocket = new Socket(slaveAdressIp, slavePort);
                DataOutputStream out = new DataOutputStream(slaveSocket.getOutputStream());
                DataInputStream in = new DataInputStream(slaveSocket.getInputStream())) {

            out.writeUTF("GET_PART");
            out.writeUTF(partFileName);

            String response = in.readUTF();
            if ("NOT_FOUND".equals(response)) {
                return null;
            }

            ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                byteOut.write(buffer, 0, bytesRead);
            }
            System.out.println("Partie obtenue : " + partFileName + " avec succès sur le sous-serveur " + slavePort);
            return byteOut.toByteArray();

        } catch (IOException e) {
            System.err.println("Erreur lors de la récupération de la partie depuis le sous-serveur " + slavePort);
            return null;
        }
    }

    private static String appendPartSuffix(String fileName, int partNumber) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex == -1) {
            return fileName + "_part" + partNumber;
        }
        String name = fileName.substring(0, dotIndex);
        String extension = fileName.substring(dotIndex);
        return name + "_part" + partNumber + extension;
    }

    private static void logFileTransfer(String originalFileName) {
        String logMessage = originalFileName + "\n";
        File logFile = new File("log/log.txt");

        try {
            // Créer le fichier log.txt s'il n'existe pas
            logFile.getParentFile().mkdirs();
            logFile.createNewFile();

            try (FileWriter writer = new FileWriter(logFile, true)) {
                writer.write(logMessage);
                System.out.println("Log ajouté : " + logMessage.trim());
            }
        } catch (IOException e) {
            System.err.println("Erreur lors de l'écriture dans le fichier log.txt : " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void handleFileListRequests(int listPort) {
        try (ServerSocket listServerSocket = new ServerSocket(listPort)) {
            System.out.println("Serveur de liste en attente sur le port " + listPort);
            while (true) {
                Socket clientSocket = null; // Déclarer en dehors du bloc try
                try {
                    clientSocket = listServerSocket.accept();
                    try (DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream());
                            BufferedReader logReader = new BufferedReader(new FileReader("log/log.txt"))) {

                        String line;
                        while ((line = logReader.readLine()) != null) {
                            out.writeUTF(line);
                        }
                        out.writeUTF("END"); // Marqueur de fin
                    }
                } catch (FileNotFoundException e) {
                    System.err.println("Fichier log.txt introuvable. Aucun fichier à afficher.");
                    if (clientSocket != null && !clientSocket.isClosed()) {
                        try (DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream())) {
                            out.writeUTF("Aucun fichier trouvé.");
                            out.writeUTF("END");
                        } catch (IOException ex) {
                            System.err.println("Erreur lors de la réponse au client : " + ex.getMessage());
                        }
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Erreur du serveur de liste : " + e.getMessage());
            e.printStackTrace();
        }
    }
}
