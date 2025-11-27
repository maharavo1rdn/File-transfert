package Client;

import java.awt.*;
import java.io.*;
import java.net.*;
import javax.swing.*;
import javax.swing.border.TitledBorder;

public class FileSenderClientGUI extends JFrame {
    private JTextField filePathField;
    private JButton browseButton, sendButton, refreshButton, downloadButton, deleteButton;    private JComboBox<String> fileDropdown;
    private JFileChooser fileChooser;
    public FileSenderClientGUI() {
        setTitle("Client de Transfert de Fichier");
        setSize(700, 500);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new GridBagLayout());
        getContentPane().setBackground(new Color(240, 248, 255)); // Arrière-plan doux
    
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(8, 8, 8, 8);
    
        // Titre principal
        JLabel titleLabel = new JLabel("Client de Transfert de Fichier");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 20));
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        titleLabel.setForeground(new Color(0, 102, 204));
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 3;
        add(titleLabel, gbc);
    
        // Liste des fichiers transférés
        JLabel transferredLabel = new JLabel("Fichiers transférés :");
        transferredLabel.setFont(new Font("Arial", Font.PLAIN, 14));
        gbc.gridwidth = 1;
        gbc.gridx = 0;
        gbc.gridy = 1;
        add(transferredLabel, gbc);
    
        // Dropdown de sélection des fichiers
        fileDropdown = new JComboBox<>();
        fileDropdown.setBackground(Color.WHITE);
        fileDropdown.setBorder(BorderFactory.createLineBorder(new Color(180, 180, 180)));
        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.weightx = 0.7; // Largeur flexible pour le dropdown
        add(fileDropdown, gbc);
    
        // Bouton "Rafraîchir" à droite du dropdown
        refreshButton = new JButton("Rafraîchir");
        styleButton(refreshButton, new Color(102, 204, 255));
        gbc.gridx = 2; // À droite du dropdown
        gbc.weightx = 0.3; // Taille réduite
        add(refreshButton, gbc);
    
        // Panel pour les boutons "Télécharger" et "Supprimer" sous le dropdown
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        buttonPanel.setBackground(new Color(240, 248, 255));
    
        downloadButton = new JButton("Télécharger");
        downloadButton.setEnabled(false);
        styleButton(downloadButton, new Color(204, 102, 0));
        buttonPanel.add(downloadButton);
    
        deleteButton = new JButton("Supprimer");
        deleteButton.setEnabled(false);
        styleButton(deleteButton, new Color(204, 0, 0));
        buttonPanel.add(deleteButton);
    
        gbc.gridx = 1;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        add(buttonPanel, gbc);
    
        // Sélection de fichier à envoyer
        JLabel sendFileLabel = new JLabel("Fichier à envoyer :");
        sendFileLabel.setFont(new Font("Arial", Font.PLAIN, 14));
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 1;
        add(sendFileLabel, gbc);
    
        filePathField = new JTextField();
        filePathField.setEditable(false);
        filePathField.setBackground(Color.WHITE);
        gbc.gridx = 1;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        add(filePathField, gbc);
    
        // Bouton "Parcourir"
        browseButton = new JButton("Parcourir");
        styleButton(browseButton, new Color(102, 204, 255));
        gbc.gridx = 1;
        gbc.gridy = 4;
        gbc.gridwidth = 2;
        add(browseButton, gbc);
    
        // Bouton "Envoyer"
        sendButton = new JButton("Envoyer");
        sendButton.setEnabled(false);
        styleButton(sendButton, new Color(0, 153, 51));
        gbc.gridx = 1;
        gbc.gridy = 5;
        gbc.gridwidth = 2;
        add(sendButton, gbc);
    
        fileChooser = new JFileChooser();
    
        // Ajouter bordure principale
        setBorderAroundComponents();
    
        // Actions
        browseButton.addActionListener(e -> chooseFile());
        sendButton.addActionListener(e -> sendFile());
        refreshButton.addActionListener(e -> fetchTransferredFiles());
        downloadButton.addActionListener(e -> downloadFile());
        deleteButton.addActionListener(e -> deleteFile());
    
        fileDropdown.addActionListener(e -> {
            boolean isSelected = fileDropdown.getSelectedItem() != null;
            downloadButton.setEnabled(isSelected);
            deleteButton.setEnabled(isSelected);
        });
    
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private void chooseFile() {
        int returnValue = fileChooser.showOpenDialog(this);
        if (returnValue == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            filePathField.setText(selectedFile.getAbsolutePath());
            sendButton.setEnabled(true);
        }
    }

    private void styleButton(JButton button, Color backgroundColor) {
        button.setBackground(backgroundColor);
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setFont(new Font("Arial", Font.BOLD, 15));
        button.setBorder(BorderFactory.createLineBorder(new Color(100, 100, 100), 1));
        button.setPreferredSize(new Dimension(100,30));
    }

    private void setBorderAroundComponents() {
        TitledBorder border = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(180, 180, 180), 2),
                "Interface Utilisateur", TitledBorder.CENTER, TitledBorder.TOP,
                new Font("Arial", Font.BOLD, 14), new Color(100, 100, 100));
        JPanel panel = new JPanel();
        panel.setLayout(new GridBagLayout());
        panel.setBackground(new Color(240, 248, 255));
        panel.setBorder(border);
        panel.add(this.getContentPane());
        setContentPane(panel);
    }

    private void sendFile() {
        String filePath = filePathField.getText();
        int port = 12345;

        try (Socket socket = new Socket("localhost", port);
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                FileInputStream fileInputStream = new FileInputStream(filePath)) {

            File file = new File(filePath);
            long totalFileSize = file.length();

            out.writeUTF(file.getName());
            out.writeLong(totalFileSize);

            byte[] buffer = new byte[8192];
            int bytesRead;

            while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }

            JOptionPane.showMessageDialog(this, "Fichier envoyé avec succès ! (" + totalFileSize + " octets)",
                    "Succès", JOptionPane.INFORMATION_MESSAGE);

        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Erreur lors de l'envoi du fichier : " + e.getMessage(),
                    "Erreur", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void fetchTransferredFiles() {
        int port = 12346;
        try (Socket socket = new Socket("localhost", port);
                DataInputStream in = new DataInputStream(socket.getInputStream())) {

            fileDropdown.removeAllItems();
            String fileName;
            while (!(fileName = in.readUTF()).equals("END")) {
                fileDropdown.addItem(fileName);
            }
            JOptionPane.showMessageDialog(this, "Liste des fichiers mise à jour !", "Succès",
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                    "Erreur lors de la récupération de la liste des fichiers : " + e.getMessage(),
                    "Erreur", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void downloadFile() {
        String fileName = (String) fileDropdown.getSelectedItem();
        if (fileName == null) {
            JOptionPane.showMessageDialog(this, "Veuillez sélectionner un fichier à télécharger.",
                    "Erreur", JOptionPane.ERROR_MESSAGE);
            return;
        }

        int port = 12347; // Port pour les téléchargements
        try (Socket socket = new Socket("localhost", port);
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                DataInputStream in = new DataInputStream(socket.getInputStream())) {

            out.writeUTF(fileName);

            File downloadedFile = new File("download/" + fileName);
            downloadedFile.getParentFile().mkdirs();

            try (BufferedOutputStream fileOut = new BufferedOutputStream(new FileOutputStream(downloadedFile))) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    fileOut.write(buffer, 0, bytesRead);
                }
            }

            JOptionPane.showMessageDialog(this, "Fichier téléchargé avec succès dans le dossier 'download' !",
                    "Succès", JOptionPane.INFORMATION_MESSAGE);

        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Erreur lors du téléchargement du fichier : " + e.getMessage(),
                    "Erreur", JOptionPane.ERROR_MESSAGE);
        }
    }
    private void deleteFile() {
        String fileName = (String) fileDropdown.getSelectedItem();
        int port = 12348;
        try (Socket socket = new Socket("localhost", port);
             DataOutputStream out = new DataOutputStream(socket.getOutputStream());
             DataInputStream in = new DataInputStream(socket.getInputStream())) {

            out.writeUTF(fileName);
            String response = in.readUTF();

            if ("SUCCESS".equals(response)) {
                JOptionPane.showMessageDialog(this, "Fichier supprimé avec succès !", "Succès", JOptionPane.INFORMATION_MESSAGE);
                fetchTransferredFiles();
            } else {
                JOptionPane.showMessageDialog(this, "Erreur : Suppression échouée.", "Erreur", JOptionPane.ERROR_MESSAGE);
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Erreur suppression : " + e.getMessage(), "Erreur", JOptionPane.ERROR_MESSAGE);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(FileSenderClientGUI::new);
    }
}
