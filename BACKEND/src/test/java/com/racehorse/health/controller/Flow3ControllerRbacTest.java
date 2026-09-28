package com.racehorse.health.controller;

import com.racehorse.health.dto.AddInjuryRecoveryUpdateRequest;
import com.racehorse.health.dto.AddMedicalExaminationAddendumRequest;
import com.racehorse.health.dto.CreateInjuryMarkerRequest;
import com.racehorse.health.dto.CreateMedicalExaminationRequest;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class Flow3ControllerRbacTest {

    @Test
    void barnHealthControllerRbac() throws Exception {
        Method method = BarnHealthController.class.getDeclaredMethod("getBarnMap", String.class, UUID.class);
        PreAuthorize auth = method.getAnnotation(PreAuthorize.class);
        assertThat(auth).isNotNull();
        assertThat(auth.value()).isEqualTo(
                "hasAuthority('PERM_MEDICAL_VIEW') and hasAnyRole('VETERINARIAN','CLUB_MANAGER','HEAD_TRAINER','STABLE_STAFF')");
    }

    @Test
    void medicalExaminationControllerRbac() throws Exception {
        Method createMethod = MedicalExaminationController.class.getDeclaredMethod(
                "create", UUID.class, Jwt.class, CreateMedicalExaminationRequest.class);
        PreAuthorize createAuth = createMethod.getAnnotation(PreAuthorize.class);
        assertThat(createAuth).isNotNull();
        assertThat(createAuth.value()).isEqualTo("hasRole('VETERINARIAN') and hasAuthority('PERM_MEDICAL_EDIT')");

        Method addendumMethod = MedicalExaminationController.class.getDeclaredMethod(
                "addAddendum", UUID.class, Jwt.class, AddMedicalExaminationAddendumRequest.class);
        PreAuthorize addendumAuth = addendumMethod.getAnnotation(PreAuthorize.class);
        assertThat(addendumAuth).isNotNull();
        assertThat(addendumAuth.value()).isEqualTo("hasRole('VETERINARIAN') and hasAuthority('PERM_MEDICAL_EDIT')");

        Method getDetailMethod = MedicalExaminationController.class.getDeclaredMethod(
                "getDetail", UUID.class, Jwt.class, Authentication.class);
        PreAuthorize getDetailAuth = getDetailMethod.getAnnotation(PreAuthorize.class);
        assertThat(getDetailAuth).isNotNull();
        assertThat(getDetailAuth.value()).isEqualTo(
                "hasAuthority('PERM_MEDICAL_VIEW') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER','VETERINARIAN','HORSE_OWNER')");

        Method listMethod = MedicalExaminationController.class.getDeclaredMethod(
                "listByHorse", UUID.class, Jwt.class, Authentication.class);
        PreAuthorize listAuth = listMethod.getAnnotation(PreAuthorize.class);
        assertThat(listAuth).isNotNull();
        assertThat(listAuth.value()).isEqualTo(
                "hasAuthority('PERM_MEDICAL_VIEW') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER','VETERINARIAN','HORSE_OWNER')");
    }

    @Test
    void injuryControllerRbac() throws Exception {
        Method createMethod = InjuryController.class.getDeclaredMethod(
                "create", UUID.class, Jwt.class, CreateInjuryMarkerRequest.class);
        PreAuthorize createAuth = createMethod.getAnnotation(PreAuthorize.class);
        assertThat(createAuth).isNotNull();
        assertThat(createAuth.value()).isEqualTo("hasRole('VETERINARIAN') and hasAuthority('PERM_MEDICAL_EDIT')");

        Method updateMethod = InjuryController.class.getDeclaredMethod(
                "addRecoveryUpdate", UUID.class, Jwt.class, AddInjuryRecoveryUpdateRequest.class);
        PreAuthorize updateAuth = updateMethod.getAnnotation(PreAuthorize.class);
        assertThat(updateAuth).isNotNull();
        assertThat(updateAuth.value()).isEqualTo("hasRole('VETERINARIAN') and hasAuthority('PERM_MEDICAL_EDIT')");

        Method getDetailMethod = InjuryController.class.getDeclaredMethod(
                "getDetail", UUID.class, Jwt.class, Authentication.class);
        PreAuthorize getDetailAuth = getDetailMethod.getAnnotation(PreAuthorize.class);
        assertThat(getDetailAuth).isNotNull();
        assertThat(getDetailAuth.value()).isEqualTo(
                "hasAuthority('PERM_MEDICAL_VIEW') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER','VETERINARIAN','HORSE_OWNER')");

        Method listMethod = InjuryController.class.getDeclaredMethod(
                "listByHorse", UUID.class, String.class, Jwt.class, Authentication.class);
        PreAuthorize listAuth = listMethod.getAnnotation(PreAuthorize.class);
        assertThat(listAuth).isNotNull();
        assertThat(listAuth.value()).isEqualTo(
                "hasAuthority('PERM_MEDICAL_VIEW') and hasAnyRole('CLUB_MANAGER','HEAD_TRAINER','VETERINARIAN','HORSE_OWNER')");
    }

    @Test
    void trainingLockControllerRbacIncludesHorseOwnerForRead() throws Exception {
        Method currentMethod = TrainingLockController.class.getDeclaredMethod(
                "current", UUID.class, Jwt.class, Authentication.class);
        PreAuthorize currentAuth = currentMethod.getAnnotation(PreAuthorize.class);
        assertThat(currentAuth).isNotNull();
        assertThat(currentAuth.value()).isEqualTo(
                "hasAuthority('PERM_MEDICAL_VIEW') and hasAnyRole('VETERINARIAN','CLUB_MANAGER','HEAD_TRAINER','HORSE_OWNER')");

        Method historyMethod = TrainingLockController.class.getDeclaredMethod(
                "history", UUID.class, Jwt.class, Authentication.class);
        PreAuthorize historyAuth = historyMethod.getAnnotation(PreAuthorize.class);
        assertThat(historyAuth).isNotNull();
        assertThat(historyAuth.value()).isEqualTo(
                "hasAuthority('PERM_MEDICAL_VIEW') and hasAnyRole('VETERINARIAN','CLUB_MANAGER','HEAD_TRAINER','HORSE_OWNER')");
    }

    @Test
    void preventiveCareControllerListDueRbac() throws Exception {
        Method dueMethod = PreventiveCareController.class.getDeclaredMethod(
                "listDue", LocalDate.class, String.class, boolean.class);
        PreAuthorize dueAuth = dueMethod.getAnnotation(PreAuthorize.class);
        assertThat(dueAuth).isNotNull();
        assertThat(dueAuth.value()).isEqualTo(
                "hasAuthority('PERM_MEDICAL_VIEW') and hasAnyRole('VETERINARIAN','CLUB_MANAGER','HEAD_TRAINER')");
    }
}
