package org.mdpnp.apps.testapp.patient;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import javax.sql.DataSource;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;

public class PatientInfo {

    enum Gender { male, female };

    private final StringProperty lastName = new SimpleStringProperty();
    private final StringProperty firstName = new SimpleStringProperty();
    private final StringProperty mrn = new SimpleStringProperty();
    private final ObjectProperty<Date> dob = new SimpleObjectProperty<>();
    private final ObjectProperty<Gender> gender = new SimpleObjectProperty<>();

    public PatientInfo(String mrn, String fn, String ln) {
        this.mrn.setValue(mrn);
        this.firstName.setValue(fn);
        this.lastName.setValue(ln);
    }

    public String getLastName() {
        return lastName.get();
    }

    public StringProperty lastNameProperty() {
        return lastName;
    }

    public String getFirstName() {
        return firstName.get();
    }

    public StringProperty firstNameProperty() {
        return firstName;
    }

    public String getMrn() {
        return mrn.get();
    }

    public StringProperty mrnProperty() {
        return mrn;
    }

    public Date getDob() {
        return dob.get();
    }

    public ObjectProperty<Date> dobProperty() {
        return dob;
    }

    public void setDob(Date dob) {
        this.dob.set(dob);
    }

    public Gender getGender() {
        return gender.get();
    }

    public ObjectProperty<Gender> genderProperty() {
        return gender;
    }

    public void setGender(Gender gender) {
        this.gender.set(gender);
    }

    @Override
    public String toString() {
        return firstName.getValue() + " " + lastName.getValue();

    }

    static class PatientInfoRowMapper implements RowMapper<PatientInfo> {
        public PatientInfo mapRow(ResultSet rs, int rowNum) throws SQLException {
            PatientInfo p = new PatientInfo(
                    rs.getString("MRN"),
                    rs.getString("FIRST_NAME"),
                    rs.getString("LAST_NAME"));
            return p;
        }
    }

    //
    // DAO APIs
    //
    private static final String CREATE_SQL=
            "INSERT INTO PATIENT_INFO (MRN,LAST_NAME,FIRST_NAME,DOB,GENDER) VALUES  (?,?,?,?,?)";

    static boolean createPatient(DataSource dataSource, PatientInfo p) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.update(CREATE_SQL,
                            new Object[]{
                                    p.getMrn(),
                                    p.getFirstName(),
                                    p.getLastName(),
                                    p.getDob(),
                                    p.getGender()
                            });

        return true;
    }

    static List<PatientInfo> queryAll(DataSource dataSource) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        List<PatientInfo> l = jdbcTemplate.query("select * from PATIENT_INFO", new PatientInfoRowMapper());
        return l;
    }

    @Override
    public boolean equals(Object obj) {
        if(obj instanceof PatientInfo) {
            return mrn.equals(((PatientInfo)obj).mrn);
        } else {
            return false;
        }
    }
    @Override
    public int hashCode() {
        return mrn.hashCode();
    }

}
