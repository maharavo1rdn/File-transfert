package Client;

import java.io.*;
import java.net.Socket;
import java.util.*;

public class FileSenderClientCLI {
    private static final String SERVER_HOST = "localhost";
    private static final int SEND_PORT = 12345;
    private static final int REFRESH_PORT = 12346;
    private static final int DOWNLOAD_PORT = 12347;
    private static final int DELETE_PORT = 12348;

    private static List<String> fileList = new ArrayList<>();
    private static Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        System.out.println("Bienvenue dans le Client de Transfert de Fichier.");
        System.out.println("Commandes disponibles :");
        System.out.println(" - send <filepath>");
        System.out.println(" - refresh");
        System.out.println(" - list");
        System.out.println(" - download <filename>");
        System.out.println(" - delete <filename>");
        System.out.println(" - exit");

        while (true) {
            System.out.print("\n> ");
            String input = scanner.nextLine().trim();
            String[] command = input.split("\\s+", 2);

            switch (command[0].toLowerCase()) {
                case "send":
                    if (command.length > 1) sendFile(command[1]);
                    else System.out.println("Syntaxe : send <filepath>");
                    break;
                case "refresh":
                    fetchTransferredFiles();
                    break;
                case "list":
                    listFiles();
                    break;
                case "download":
                    if (command.length > 1) downloadFile(command[1]);
                    else System.out.println("Syntaxe : download <filename>");
                    break;
                case "delete":
                    if (command.length > 1) deleteFile(command[1]);
                    else System.out.println("Syntaxe : delete <filename>");
                    break;
                case "exit":
                    System.out.println("Au revoir !");
                    System.exit(0);
                default:
                    System.out.println("Commande inconnue. Essayez encore.");
            }
        }
    }

    private static void sendFile(String filePath) {
        File file = new File(filePath);
        if (!file.exists() || !file.isFile()) {
            System.out.println("Erreur : Fichier invalide.");
            return;
        }

        try (Socket socket = new Socket(SERVER_HOST, SEND_PORT);
             DataOutputStream out = new DataOutputStream(socket.getOutputStream());
             FileInputStream fileInputStream = new FileInputStream(file)) {

            System.out.println("Envoi du fichier : " + file.getName());
            out.writeUTF(file.getName());
            out.writeLong(file.length());

            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }

            System.out.println("Fichier envoyé avec succès : " + file.length() + " octets.");
        } catch (IOException e) {
            System.out.println("Erreur d'envoi : " + e.getMessage());
        }
    }

    private static void fetchTransferredFiles() {
        System.out.println("Récupération de la liste des fichiers...");
        fileList.clear();

        try (Socket socket = new Socket(SERVER_HOST, REFRESH_PORT);
             DataInputStream in = new DataInputStream(socket.getInputStream())) {

            String fileName;
            while (!(fileName = in.readUTF()).equals("END")) {
                fileList.add(fileName);
            }
            System.out.println("Liste mise à jour. Utilisez 'list' pour voir les fichiers.");
        } catch (IOException e) {
            System.out.println("Erreur de récupération : " + e.getMessage());
        }
    }

    private static void listFiles() {
        if (fileList.isEmpty()) {
            System.out.println("Aucun fichier disponible. Rafraîchissez avec 'refresh'.");
        } else {
            System.out.println("Fichiers disponibles :");
            for (String file : fileList) {
                System.out.println(" - " + file);
            }
        }
    }

    private static void downloadFile(String fileName) {
        if (!fileList.contains(fileName)) {
            System.out.println("Erreur : Le fichier spécifié n'existe pas. Rafraîchissez avec 'refresh'.");
            return;
        }

        File downloadFolder = new File("download");
        if (!downloadFolder.exists()) downloadFolder.mkdirs();

        try (Socket socket = new Socket(SERVER_HOST, DOWNLOAD_PORT);
             DataOutputStream out = new DataOutputStream(socket.getOutputStream());
             DataInputStream in = new DataInputStream(socket.getInputStream())) {

            out.writeUTF(fileName);

            File downloadedFile = new File(downloadFolder, fileName);
            try (BufferedOutputStream fileOut = new BufferedOutputStream(new FileOutputStream(downloadedFile))) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    fileOut.write(buffer, 0, bytesRead);
                }
            }

            System.out.println("Fichier téléchargé : " + downloadedFile.getAbsolutePath());
        } catch (IOException e) {
            System.out.println("Erreur de téléchargement : " + e.getMessage());
        }
    }

    private static void deleteFile(String fileName) {
        if (!fileList.contains(fileName)) {
            System.out.println("Erreur : Le fichier spécifié n'existe pas. Rafraîchissez avec 'refresh'.");
            return;
        }

        try (Socket socket = new Socket(SERVER_HOST, DELETE_PORT);
             DataOutputStream out = new DataOutputStream(socket.getOutputStream());
             DataInputStream in = new DataInputStream(socket.getInputStream())) {

            out.writeUTF(fileName);
            String response = in.readUTF();

            if ("SUCCESS".equals(response)) {
                System.out.println("Fichier supprimé avec succès.");
                fileList.remove(fileName);
            } else {
                System.out.println("Erreur lors de la suppression du fichier.");
            }
        } catch (IOException e) {
            System.out.println("Erreur de suppression : " + e.getMessage());
        }
    }
}
