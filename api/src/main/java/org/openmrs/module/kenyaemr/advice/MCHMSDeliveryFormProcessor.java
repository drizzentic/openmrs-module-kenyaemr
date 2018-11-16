package org.openmrs.module.kenyaemr.advice;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.poi.ss.formula.functions.T;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.joda.time.Period;
import org.openmrs.*;
import org.openmrs.api.ConceptService;
import org.openmrs.api.EncounterService;
import org.openmrs.api.ObsService;
import org.openmrs.api.PersonService;
import org.openmrs.api.context.Context;
import org.openmrs.module.idgen.service.IdentifierSourceService;
import org.openmrs.module.kenyaemr.metadata.MchMetadata;
import org.openmrs.module.kenyaemr.wrapper.PatientWrapper;
import org.openmrs.module.metadatadeploy.MetadataUtils;
import org.openmrs.util.PrivilegeConstants;
import org.springframework.aop.AfterReturningAdvice;
import org.openmrs.module.kenyaemr.Dictionary;


import java.lang.reflect.Method;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

public class MCHMSDeliveryFormProcessor implements AfterReturningAdvice {

    private Log log = LogFactory.getLog(this.getClass());
    public static final String DELIVERY_FORMUUID = "496c7cc3-0eea-4e84-a04c-2292949e2f7f";
    public static final String OPENMRS_ID = "dfacd928-0370-4315-99d7-6ec1c9f7ae76";
    @Override
    public void afterReturning(Object returnValue, Method method, Object[] args, Object target) throws Throwable {

        String deliveryOutcomeGroupingConcept = "162588AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
        EncounterService encounterService = Context.getEncounterService();
        ObsService obsService = Context.getObsService();
        ConceptService conceptService = Context.getConceptService();
        PersonService personService = Context.getPersonService();
        if (method.getName().equals("saveEncounter")) {
            Encounter enc = (Encounter) args[0];

            if(enc != null && enc.getForm().getUuid().equals(DELIVERY_FORMUUID)) {      //EncounterType 15 MCH
                boolean errorOccured = false;
                Person parent = personService.getPerson(enc.getPatient().getPersonId());
                // construct object for each baby and process
                List<Obs> obs = obsService.getObservations(
                        Arrays.asList(personService.getPerson(enc.getPatient().getPersonId())),
                        Arrays.asList(enc),
                        Arrays.asList(conceptService.getConceptByUuid(deliveryOutcomeGroupingConcept)),
                        null,
                        null,
                        null,
                        Arrays.asList("obsId"),
                        null,
                        null,
                        null,
                        null,
                        false
                );
                for(Obs o: obs) {
                    Patient baby = extractBabyRegistrationDetails(o.getGroupMembers());
                    if (baby != null) {

                        baby.setBirthdate(o.getObsDatetime());
                        // Make sure everyone gets an OpenMRS ID
                        PatientIdentifierType openmrsIdType = MetadataUtils.existing(PatientIdentifierType.class, OPENMRS_ID);
                        PatientIdentifier openmrsId = baby.getPatientIdentifier(openmrsIdType);

                        if (openmrsId == null) {
                            String generated = Context.getService(IdentifierSourceService.class).generateIdentifier(openmrsIdType, "Registration");
                            openmrsId = new PatientIdentifier(generated, openmrsIdType, getDefaultLocation());
                            baby.addIdentifier(openmrsId);

                            if (!baby.getPatientIdentifier().isPreferred()) {
                                openmrsId.setPreferred(true);
                            }
                        }
                        try {

                           baby = Context.getPatientService().savePatient(baby);
                           addRelationship(parent, baby);
//                            System.out.println("Name: " + baby.getNames());
//                            System.out.println("Dobb: " + baby.getBirthdate());
                        } catch (Exception e) {
                            e.printStackTrace();
                            errorOccured = true;
                        }
                    }
                }

            }
        }

   }


    private Patient fillBabyName(String fullName, Patient patient) {
        if (fullName != null && !fullName.equals("")) {
            String[] nameParts = StringUtils.split(fullName);
            PersonName pn = new PersonName();
            if(nameParts.length == 1) {
                pn.setGivenName(fullName);
                pn.setFamilyName("Baby ");
            } else if(nameParts.length == 2) {
                pn.setFamilyName(nameParts[0]);
                pn.setGivenName(nameParts[1]);
            } else {
                pn.setGivenName(nameParts[0]);
                pn.setMiddleName(nameParts[1]);
                pn.setFamilyName(nameParts[2]);
            }


            patient.addName(pn);

            return  patient;


        }
      return null;
    }

    private Patient extractBabyRegistrationDetails (Set<Obs> obsList) {
        Integer babyName = 1586;
         Integer sexConcept = 1587;
        Integer dobConcept = 5599;

        String patientName = null;
        String sex = null;
        Date dob = null;

        for (Obs obs : obsList) {

            if (obs.getConcept().getConceptId().equals(babyName)) {
                patientName = obs.getValueText();
            } else if (obs.getConcept().getConceptId().equals(dobConcept)) { // get age
                dob = obs.getValueDatetime();
                //System.out.println("DOB concept up " +dob);
            } else if (obs.getConcept().getConceptId().equals(sexConcept)) {
                sex = sexConverter(obs.getValueCoded());
            }
        }
            if (patientName != null) {

                Patient baby = new Patient();

                fillBabyName(patientName, baby);
               // baby.setBirthdate(dob);
                baby.setGender(sex);
                return baby;

            }

            return null;
        }


    private void addRelationship(Person parent, Person child) {



/*+----------------------+--------------------------------------+------------+--------------+
| relationship_type_id | uuid                                 | a_is_to_b  | b_is_to_a    |
+----------------------+--------------------------------------+------------+--------------+
|                    1 | 8d919b58-c2cc-11de-8d13-0010c6dffd0f | Doctor     | Patient      |
|                    2 | 8d91a01c-c2cc-11de-8d13-0010c6dffd0f | Sibling    | Sibling      |
|                    3 | 8d91a210-c2cc-11de-8d13-0010c6dffd0f | Parent     | Child        |
|                    4 | 8d91a3dc-c2cc-11de-8d13-0010c6dffd0f | Aunt/Uncle | Niece/Nephew |
|                    5 | 5f115f62-68b7-11e3-94ee-6bef9086de92 | Guardian   | Dependant    |
|                    6 | d6895098-5d8d-11e3-94ee-b35a4132a5e3 | Spouse     | Spouse       |
|                    7 | 007b765f-6725-4ae9-afee-9966302bace4 | Partner    | Partner      |
|                    8 | 2ac0d501-eadc-4624-b982-563c70035d46 | Co-wife    | Co-wife      |
+----------------------+--------------------------------------+------------+--------------+
*/
        PersonService personService = Context.getPersonService();
        RelationshipType rType = personService.getRelationshipTypeByUuid("8d91a210-c2cc-11de-8d13-0010c6dffd0f");

        Relationship rel = new Relationship();
        rel.setRelationshipType(rType);
        rel.setPersonA(parent);
        rel.setPersonB(child);

        Context.getPersonService().saveRelationship(rel);
    }

    public Location getDefaultLocation() {
        try {
            Context.addProxyPrivilege(PrivilegeConstants.VIEW_LOCATIONS);
            Context.addProxyPrivilege(PrivilegeConstants.VIEW_GLOBAL_PROPERTIES);
            String GP_DEFAULT_LOCATION = "kenyaemr.defaultLocation";
            GlobalProperty gp = Context.getAdministrationService().getGlobalPropertyObject(GP_DEFAULT_LOCATION);
            return gp != null ? ((Location) gp.getValue()) : null;
        }
        finally {
            Context.removeProxyPrivilege(PrivilegeConstants.VIEW_LOCATIONS);
            Context.removeProxyPrivilege(PrivilegeConstants.VIEW_GLOBAL_PROPERTIES);
        }

    }
    String sexConverter (Concept key) {
        ConceptService conceptService = Context.getConceptService();

        Map<Concept, String> sexOptions = new HashMap<Concept, String>();
        sexOptions.put(conceptService.getConcept(1534), "M");
        sexOptions.put(conceptService.getConcept(1535), "F");
        return sexOptions.get(key);
    }

}




