import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;

class Member {
    String id, name, phone, plan;
    GregorianCalendar expiry;
    int totalPaid;
    int dbId;          // members.member_id in the database
}

public class GymManagerGUI extends JFrame {
    private final ArrayList<Member> members = new ArrayList<Member>();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy");

    // pending registration (set by "Add to Registration")
    private String pendingPlan = "";
    private int pendingMonths = 0;
    private int pendingFee = 0;

    // Register tab components
    private JTextField nameField, phoneField, payField;
    private JComboBox<String> planBox;
    private JComboBox<Integer> monthsBox;
    private DefaultTableModel orderModel;
    private JLabel totalLabel, statusLabel;

    // Manage tab components
    private JTextField searchField;
    private DefaultTableModel memberModel;
    private JTable memberTable;

    public GymManagerGUI() {
        setTitle("Group 6 Gym Membership System");
        setSize(980, 600);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Register Member", buildRegisterTab());
        tabs.addTab("Manage Members", buildManageTab());
        add(tabs);

        loadMembers();
        refreshTable("");
    }

    // ===================== TAB 1: REGISTER MEMBER =====================
    private JPanel buildRegisterTab() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // top form
        nameField = new JTextField();
        phoneField = new JTextField();
        planBox = new JComboBox<String>(new String[]{"Basic", "Premium", "Student"});
        monthsBox = new JComboBox<Integer>(new Integer[]{1, 3, 6, 12});
        JButton addButton = new JButton("Add to Registration");
        JButton clearButton = new JButton("Clear");

        JPanel form = new JPanel(new GridLayout(3, 4, 10, 8));
        form.add(new JLabel("Member Name:"));
        form.add(nameField);
        form.add(new JLabel("Membership Plan:"));
        form.add(planBox);
        form.add(new JLabel("Mobile Number:"));
        form.add(phoneField);
        form.add(new JLabel("Months:"));
        form.add(monthsBox);
        form.add(new JLabel(""));
        form.add(new JLabel(""));
        form.add(addButton);
        form.add(clearButton);
        panel.add(form, BorderLayout.NORTH);

        // center table
        orderModel = new DefaultTableModel(
                new String[]{"Plan", "Monthly Rate (PHP)", "Months", "Subtotal (PHP)"}, 0) {
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };
        JTable orderTable = new JTable(orderModel);
        panel.add(new JScrollPane(orderTable), BorderLayout.CENTER);

        // bottom
        totalLabel = new JLabel("Total: PHP 0.00");
        totalLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        payField = new JTextField();
        JButton payButton = new JButton("Pay / Complete Registration");
        statusLabel = new JLabel("A member is saved only after successful payment.");

        JPanel bottom = new JPanel(new GridLayout(2, 3, 10, 8));
        bottom.add(totalLabel);
        bottom.add(new JLabel("Payment (PHP):"));
        bottom.add(payField);
        bottom.add(payButton);
        bottom.add(statusLabel);
        bottom.add(new JLabel(""));
        panel.add(bottom, BorderLayout.SOUTH);

        addButton.addActionListener(e -> addToRegistration());
        clearButton.addActionListener(e -> clearRegistration());
        payButton.addActionListener(e -> completeRegistration());

        return panel;
    }

    private void addToRegistration() {
        String plan = (String) planBox.getSelectedItem();
        int months = (Integer) monthsBox.getSelectedItem();
        int rate = getMonthlyRate(plan);
        int fee = calculateFee(plan, months);

        pendingPlan = plan;
        pendingMonths = months;
        pendingFee = fee;

        orderModel.setRowCount(0);
        orderModel.addRow(new Object[]{plan, rate, months, rate * months});
        totalLabel.setText(String.format("Total: PHP %.2f", (double) fee));
        if (plan.equals("Premium")) {
            statusLabel.setText("Premium 10% discount applied.");
        } else {
            statusLabel.setText("Plan added. Enter payment.");
        }
    }

    private void clearRegistration() {
        nameField.setText("");
        phoneField.setText("");
        payField.setText("");
        planBox.setSelectedIndex(0);
        monthsBox.setSelectedIndex(0);
        orderModel.setRowCount(0);
        pendingPlan = "";
        pendingMonths = 0;
        pendingFee = 0;
        totalLabel.setText("Total: PHP 0.00");
        statusLabel.setText("A member is saved only after successful payment.");
    }

    private void completeRegistration() {
        String name = nameField.getText().trim();
        String phone = phoneField.getText().trim();

        if (name.length() == 0) {
            JOptionPane.showMessageDialog(this, "Name cannot be empty.");
            return;
        }
        if (!isValidPhone(phone)) {
            JOptionPane.showMessageDialog(this, "Phone number must have 11 digits and start with 09.");
            return;
        }
        if (pendingFee == 0) {
            JOptionPane.showMessageDialog(this, "Click \"Add to Registration\" first.");
            return;
        }

        int cash;
        try {
            cash = Integer.parseInt(payField.getText().trim());
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Payment must be a whole number (digits only).");
            return;
        }
        if (cash < pendingFee) {
            JOptionPane.showMessageDialog(this, "Insufficient cash. Short by PHP " + (pendingFee - cash));
            return;
        }

        Member m = new Member();
        m.name = name;
        m.phone = phone;
        m.plan = pendingPlan;
        m.expiry = addMonths(new GregorianCalendar(), pendingMonths);
        m.totalPaid = pendingFee;

        int receiptNo;
        try (Connection con = DB.getConnection()) {
            con.setAutoCommit(false);
            try {
                String sql = "INSERT INTO members (member_name, phone, plan_id, expiry_date, total_paid) "
                        + "SELECT ?, ?, plan_id, ?, ? FROM membership_plans WHERE plan_name = ?";
                try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, m.name);
                    ps.setString(2, m.phone);
                    ps.setDate(3, toSqlDate(m.expiry));
                    ps.setInt(4, m.totalPaid);
                    ps.setString(5, m.plan);
                    if (ps.executeUpdate() != 1) {
                        throw new SQLException("Plan not found in membership_plans: " + m.plan);
                    }
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        keys.next();
                        m.dbId = keys.getInt(1);
                    }
                }
                m.id = "GYM-" + m.dbId;
                receiptNo = insertPayment(con, m.dbId, m, "REGISTRATION", pendingMonths, pendingFee, cash, m.expiry);
                con.commit();
            } catch (SQLException ex) {
                con.rollback();
                throw ex;
            }
        } catch (SQLException ex) {
            showDbError("save the registration", ex);
            return;
        }
        members.add(m);

        showReceipt(makeReceipt("REGISTRATION RECEIPT", receiptNo, m, pendingMonths, pendingFee, cash));
        clearRegistration();
        refreshTable("");
        statusLabel.setText("Registration successful!");
    }

    // ===================== TAB 2: MANAGE MEMBERS =====================
    private JPanel buildManageTab() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        searchField = new JTextField();
        JButton searchButton = new JButton("Search");
        JButton viewAllButton = new JButton("View All");
        JButton renewButton = new JButton("Renew Selected");
        JButton cancelButton = new JButton("Cancel Membership");

        JPanel top = new JPanel(new GridLayout(2, 4, 10, 8));
        top.add(new JLabel("Search Name:"));
        top.add(searchField);
        top.add(searchButton);
        top.add(viewAllButton);
        top.add(new JLabel(""));
        top.add(new JLabel(""));
        top.add(renewButton);
        top.add(cancelButton);
        panel.add(top, BorderLayout.NORTH);

        memberModel = new DefaultTableModel(
                new String[]{"Member ID", "Name", "Plan", "Status", "Expires"}, 0) {
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };
        memberTable = new JTable(memberModel);
        panel.add(new JScrollPane(memberTable), BorderLayout.CENTER);

        searchButton.addActionListener(e -> refreshTable(searchField.getText().trim().toLowerCase()));
        viewAllButton.addActionListener(e -> {
            searchField.setText("");
            refreshTable("");
        });
        renewButton.addActionListener(e -> renewSelected());
        cancelButton.addActionListener(e -> cancelSelected());

        return panel;
    }

    private void refreshTable(String keyword) {
        memberModel.setRowCount(0);
        for (Member m : members) {
            if (m.name.toLowerCase().indexOf(keyword) != -1) {
                String status = "EXPIRED";
                if (isActive(m)) {
                    status = "ACTIVE";
                }
                memberModel.addRow(new Object[]{m.id, m.name, m.plan, status, formatDate(m.expiry)});
            }
        }
    }

    private Member getSelectedMember() {
        int row = memberTable.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Please select a member from the table first.");
            return null;
        }
        String id = (String) memberModel.getValueAt(row, 0);
        for (Member m : members) {
            if (m.id.equals(id)) {
                return m;
            }
        }
        return null;
    }

    private void renewSelected() {
        Member m = getSelectedMember();
        if (m == null) {
            return;
        }

        Object choice = JOptionPane.showInputDialog(this, "Renew for how many months?",
                "Renew Membership", JOptionPane.QUESTION_MESSAGE, null,
                new Integer[]{1, 3, 6, 12}, 1);
        if (choice == null) {
            return;
        }
        int months = (Integer) choice;
        int fee = calculateFee(m.plan, months);

        int cash = askCash(fee);
        if (cash == -1) {
            return;
        }

        GregorianCalendar start;
        if (isActive(m)) {
            start = m.expiry;
        } else {
            start = new GregorianCalendar();
        }
        GregorianCalendar newExpiry = addMonths(start, months);
        int newTotal = m.totalPaid + fee;

        int receiptNo;
        try (Connection con = DB.getConnection()) {
            con.setAutoCommit(false);
            try {
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE members SET expiry_date = ?, total_paid = ? WHERE member_id = ?")) {
                    ps.setDate(1, toSqlDate(newExpiry));
                    ps.setInt(2, newTotal);
                    ps.setInt(3, m.dbId);
                    ps.executeUpdate();
                }
                receiptNo = insertPayment(con, m.dbId, m, "RENEWAL", months, fee, cash, newExpiry);
                con.commit();
            } catch (SQLException ex) {
                con.rollback();
                throw ex;
            }
        } catch (SQLException ex) {
            showDbError("save the renewal", ex);
            return;
        }
        m.expiry = newExpiry;
        m.totalPaid = newTotal;

        showReceipt(makeReceipt("RENEWAL RECEIPT", receiptNo, m, months, fee, cash));
        refreshTable(searchField.getText().trim().toLowerCase());
    }

    private void cancelSelected() {
        Member m = getSelectedMember();
        if (m == null) {
            return;
        }
        int answer = JOptionPane.showConfirmDialog(this,
                "Cancel the membership of " + m.name + "?", "Confirm",
                JOptionPane.YES_NO_OPTION);
        if (answer == JOptionPane.YES_OPTION) {
            try (Connection con = DB.getConnection();
                 PreparedStatement ps = con.prepareStatement("DELETE FROM members WHERE member_id = ?")) {
                ps.setInt(1, m.dbId);
                ps.executeUpdate();
            } catch (SQLException ex) {
                showDbError("cancel the membership", ex);
                return;
            }
            members.remove(m);
            refreshTable(searchField.getText().trim().toLowerCase());
            JOptionPane.showMessageDialog(this, "Membership cancelled successfully.");
        }
    }

    // returns the cash received, or -1 if cancelled
    private int askCash(int fee) {
        while (true) {
            String input = JOptionPane.showInputDialog(this,
                    "PAYMENT\n\nAmount due: PHP " + fee + "\n\nEnter cash received (whole pesos):");
            if (input == null) {
                return -1;
            }
            try {
                int cash = Integer.parseInt(input.trim());
                if (cash < fee) {
                    JOptionPane.showMessageDialog(this, "Insufficient cash. Short by PHP " + (fee - cash));
                } else {
                    return cash;
                }
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Cash must be a whole number (digits only).");
            }
        }
    }

    // ===================== DATABASE HELPERS =====================
    // Reads every member from gym_db into the members list.
    private void loadMembers() {
        members.clear();
        String sql = "SELECT m.member_id, m.member_name, m.phone, p.plan_name, m.expiry_date, m.total_paid "
                + "FROM members m JOIN membership_plans p ON p.plan_id = m.plan_id "
                + "ORDER BY m.member_id";
        try (Connection con = DB.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Member m = new Member();
                m.dbId = rs.getInt("member_id");
                m.id = "GYM-" + m.dbId;
                m.name = rs.getString("member_name");
                m.phone = rs.getString("phone");
                m.plan = rs.getString("plan_name");
                GregorianCalendar expiry = new GregorianCalendar();
                expiry.setTime(rs.getDate("expiry_date"));
                m.expiry = expiry;
                m.totalPaid = rs.getBigDecimal("total_paid").intValue();
                members.add(m);
            }
        } catch (SQLException ex) {
            showDbError("load the members", ex);
        }
    }

    // Saves one receipt row and returns its payment_id (shown as RCT-<id>).
    private int insertPayment(Connection con, int memberId, Member m, String type,
                              int months, int fee, int cash, GregorianCalendar validUntil) throws SQLException {
        int rate = getMonthlyRate(m.plan);
        int subtotal = rate * months;
        int discount = subtotal - fee;
        String sql = "INSERT INTO payments (member_id, member_name, plan_name, payment_type, payment_date, "
                + "months, monthly_rate, subtotal, discount_amount, total_paid, cash_amount, change_amount, valid_until) "
                + "VALUES (?, ?, ?, ?, NOW(), ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, memberId);
            ps.setString(2, m.name);
            ps.setString(3, m.plan);
            ps.setString(4, type);
            ps.setInt(5, months);
            ps.setInt(6, rate);
            ps.setInt(7, subtotal);
            ps.setInt(8, discount);
            ps.setInt(9, fee);
            ps.setInt(10, cash);
            ps.setInt(11, cash - fee);
            ps.setDate(12, toSqlDate(validUntil));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    private java.sql.Date toSqlDate(GregorianCalendar date) {
        return new java.sql.Date(date.getTimeInMillis());
    }

    private void showDbError(String action, SQLException ex) {
        JOptionPane.showMessageDialog(this,
                "Could not " + action + ".\n\n" + ex.getMessage()
                + "\n\nCheck that Laragon is running (Start All) and the MySQL jar is on the classpath.",
                "Database error", JOptionPane.ERROR_MESSAGE);
    }

    // ===================== SHARED LOGIC (from GymManager) =====================
    private boolean isValidPhone(String phone) {
        if (phone.length() != 11) {
            return false;
        }
        if (phone.charAt(0) != '0' || phone.charAt(1) != '9') {
            return false;
        }
        for (int i = 0; i < phone.length(); i++) {
            if (!Character.isDigit(phone.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private int getMonthlyRate(String plan) {
        if (plan.equals("Basic")) {
            return 500;
        } else if (plan.equals("Premium")) {
            return 1500;
        } else if (plan.equals("Student")) {
            return 400;
        }
        return 0;
    }

    private int calculateFee(String plan, int months) {
        int fee = getMonthlyRate(plan) * months;
        if (plan.equals("Premium")) {
            fee = fee * 90 / 100;
        }
        return fee;
    }

    private GregorianCalendar addMonths(GregorianCalendar start, int months) {
        GregorianCalendar result = new GregorianCalendar(start.get(Calendar.YEAR),
                start.get(Calendar.MONTH), start.get(Calendar.DATE));
        result.add(Calendar.MONTH, months);
        return result;
    }

    private boolean isActive(Member m) {
        return dateKey(m.expiry) >= dateKey(new GregorianCalendar());
    }

    private int dateKey(GregorianCalendar date) {
        return date.get(Calendar.YEAR) * 10000 + date.get(Calendar.MONTH) * 100 + date.get(Calendar.DATE);
    }

    private String formatDate(GregorianCalendar date) {
        return dateFormat.format(date.getTime());
    }

    private String makeReceipt(String title, int receiptNo, Member m, int months, int fee, int cash) {
        int rate = getMonthlyRate(m.plan);
        int subtotal = rate * months;
        int discount = subtotal - fee;

        String receipt = "================================\n"
                + "   GROUP 6 GYM MEMBERSHIP\n"
                + "   " + title + "\n"
                + "================================\n"
                + "Receipt No: RCT-" + receiptNo + "\n"
                + "Date: " + formatDate(new GregorianCalendar()) + "\n\n"
                + "Member ID: " + m.id + "\n"
                + "Name: " + m.name + "\n"
                + "Plan: " + m.plan + "\n"
                + "Duration: " + months + " month(s)\n\n"
                + "Monthly Rate: PHP " + rate + "\n"
                + "Subtotal: PHP " + subtotal + "\n"
                + "Discount: PHP " + discount + "\n"
                + "--------------------------------\n"
                + "TOTAL PAID: PHP " + fee + "\n"
                + "Cash: PHP " + cash + "\n"
                + "Change: PHP " + (cash - fee) + "\n"
                + "--------------------------------\n"
                + "Valid Until: " + formatDate(m.expiry) + "\n\n"
                + "Thank you for your payment!";

        return receipt;
    }

    private void showReceipt(String receipt) {
        JTextArea area = new JTextArea(receipt);
        area.setFont(new Font("Monospaced", Font.PLAIN, 13));
        area.setEditable(false);
        JOptionPane.showMessageDialog(this, area, "Receipt", JOptionPane.INFORMATION_MESSAGE);
    }

    public static void main(String[] args) {
        GymManagerGUI frame = new GymManagerGUI();
        frame.setVisible(true);
    }
}