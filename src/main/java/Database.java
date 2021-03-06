import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicReference;

public class Database {

    /** The local list containing contacts. */
    private ObservableList<Contact> contacts;

    public Database() {
        contacts = FXCollections.observableArrayList();
    }

    /**
     * Since we can only create one contact a time, it is acceptable to retrieve the next contact ID from the database.
     * As a result, the auto-increment feature is made useless.
     * @return the next contact ID.
     */
    public static int getNewContactId() {
        try (Connection connection = Sql.getDataSource().getConnection()) {

            try (PreparedStatement statement = connection.prepareStatement("SELECT MAX(idcontact) AS maxid FROM " + Sql.getTableName() + ";")) {

                try (ResultSet results = statement.executeQuery()) {

                    if (results.next()) {
                        return (results.getInt("maxid") + 1);
                    }


                }
            }
        } catch (SQLException e) {
            Sql.parseSQLException(e);
        }
        throw new IllegalStateException();
    }

    /**
     * We establish a list of contacts of the second list that are not in the first list.
     * We use this for the local and remote contact lists, to establish differences and decide what contacts to add/delete.
     * @param first
     * @param second
     * @return a list of contacts.
     */
    private static ObservableList<Contact> findNotIn(ObservableList<Contact> first, ObservableList<Contact> second) {
        ObservableList<Contact> difference = FXCollections.observableArrayList();

        first.forEach(contact1 -> {
            AtomicReference<Boolean> found = new AtomicReference<>(false);
            second.forEach(contact2 -> {
                if (contact1.getId() == contact2.getId()) {
                    found.set(true);
                }
            });
            if (!found.get()) {
                difference.add(contact1);
            }
        });

        return difference;
    }

    public ObservableList<Contact> getContacts() {
        if (contacts == null) {
            contacts = FXCollections.observableArrayList();
        }
        return this.contacts;
    }

    /**
     * Retrieve contact data from database and returns it as a Contact list.
     * @return ObservableList of contacts
     */
    public ObservableList<Contact> pullFromRemote() {
        ObservableList<Contact> remoteList = FXCollections.observableArrayList();

        String s = "SELECT * FROM " + Sql.getTableName() + ";";

        try (Connection connection = Sql.getDataSource().getConnection()) {

            try (PreparedStatement statement = connection.prepareStatement(s)) {

                try (ResultSet results = statement.executeQuery()) {

                    while (results.next()) {
                        Contact cont = new Contact();
                        try {
                            cont.setPhone(Contact.toPhone(results.getString("phone")));
                            cont.setBirth(Contact.toBirth(results.getDate("birth").toString()));
                            cont.setAddress(Contact.toAddress(results.getString("address")));
                            cont.setEmail(Contact.toEmail(results.getString("email")));
                            cont.setName(new Contact.Name(results.getString("firstname"), results.getString("lastname")));
                            cont.setId(results.getInt("idcontact"));
                            remoteList.add(cont);
                        } catch (SQLException e) {
                            Sql.parseSQLException(e);
                        }


                    }

                }
            }
        } catch (SQLException e) {
            Sql.parseSQLException(e);
        }

        return remoteList;
    }

    /**
     * Initialize local contacts list.
     */
    public void startFromRemote() {

        contacts.clear();

        contacts  = pullFromRemote();

        if (contacts.isEmpty()) {
            Error.create("It appears you haven't created any contacts yet! You can import somme dummy data, using provided insertDummyData.sql script.", ContactView.parent);
        }

    }

    /**
     * This method is called each time we modify the local list, to keep the remote database updated.
     */
    private void synchronizeWithRemote() {
        ObservableList<Contact> remoteList = pullFromRemote();

        ObservableList<Contact> contactsToAddToRemote = findNotIn(contacts, remoteList);
        ObservableList<Contact> contactsToRemoveFromRemote = findNotIn(remoteList, contacts);

        if (!contactsToAddToRemote.isEmpty()) {
            addToRemote(contactsToAddToRemote);
        }
        if (!contactsToRemoveFromRemote.isEmpty()) {
            deleteFromRemote(contactsToRemoveFromRemote);
        }


    }

    /**
     * Add contact to remote database.
     * @param ol the contact to add
     */
    private void addToRemote(ObservableList<Contact> ol) {
        try (Connection connection = Sql.getDataSource().getConnection()) {

            ol.forEach(contact -> {

                try (PreparedStatement stmt = connection.prepareStatement("INSERT INTO " + Sql.getTableName() + " (lastname, firstname, phone, address, email, birth) " +
                        "VALUES (?, ?, ?, ?, ?, date(?));")) {

                    stmt.setString(1, contact.getName().getLast());
                    stmt.setString(2, contact.getName().getFirst());
                    stmt.setString(3, contact.getPhone().get());
                    stmt.setString(4, contact.getAddress().get());
                    stmt.setString(5, contact.getEmail().get());
                    stmt.setString(6, contact.getBirth().get());
                    stmt.executeUpdate();

                } catch (SQLException e) {
                    e.printStackTrace();
                }
            });

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Delete contact from remote database.
     * @param ol the contact to delete
     */
    private void deleteFromRemote(ObservableList<Contact> ol) {
        try (Connection connection = Sql.getDataSource().getConnection()) {

            ol.forEach(contact -> {

                try (PreparedStatement stmt = connection.prepareStatement("DELETE FROM " + Sql.getTableName() + " WHERE (`idcontact` = ?);")) {

                    stmt.setString(1, Integer.toString(contact.getId()));
                    stmt.executeUpdate();

                } catch (SQLException e) {
                    e.printStackTrace();
                }
            });

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void deleteContact(Contact c) {
        contacts.remove(c);
        synchronizeWithRemote();
    }

    public void addContact(Contact c) {
        contacts.add(c);
        synchronizeWithRemote();
    }

    /**
     * Update remote contact.
     * @param selected the contact to update.
     */
    public void updateContact(Contact selected) {
        try (Connection connection = Sql.getDataSource().getConnection()) {

            try (PreparedStatement stmt = connection.prepareStatement("UPDATE " + Sql.getTableName() + " " +
                    "SET firstname=?, lastname=?,phone=?,address=?,email=?,birth=date(?) WHERE idcontact=?;")) {

                stmt.setString(1, selected.getName().getFirst());
                stmt.setString(2, selected.getName().getLast());
                stmt.setString(3, selected.getPhone().get());
                stmt.setString(4, selected.getAddress().get());
                stmt.setString(5, selected.getEmail().get());
                stmt.setString(6, selected.getBirth().get());
                stmt.setString(7, Integer.toString(selected.getId()));
                stmt.executeUpdate();

            } catch (SQLException e) {
                e.printStackTrace();
            }


        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static class Lengths {
        static final public int PHONE = 15;
        static final public int EMAIL = 150;
        static final public int ADDRESS = 200;
        static final public int NAME = 45;
    }

}
