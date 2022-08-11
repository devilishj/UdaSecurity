package catpoint;

import com.udacity.catpoint.data.*;
import com.udacity.catpoint.imageServices.AwsImageService;
import com.udacity.catpoint.service.SecurityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SecurityServiceTest {

    public boolean isCat;

    public SecurityService securityService;

    @Mock
    public AwsImageService imageService;

    @Mock
    public SecurityRepository securityRepository;

    @Mock
    public Sensor sensor;

    @BeforeEach
    public void setUp() {
        securityService = new SecurityService(securityRepository, imageService);
    }

    private Set<Sensor> getSensors(boolean active, int count){
        String randomString = UUID.randomUUID().toString();

        Set<Sensor> sensors = new HashSet<>();
        for (int i = 0; i <= count; i++){
            sensors.add(new Sensor(randomString, SensorType.DOOR));
        }
        sensors.forEach(it -> it.setActive(active));
        return sensors;
    }

    @Test
    @Order(1)
    @DisplayName(
            "1. If alarm is armed and a sensor becomes activated, put the system into pending alarm status.")
    public void alarmArmed_sensorActivated_alarmPending() {
        when(sensor.getActive()).thenReturn(false);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.NO_ALARM);
        securityService.changeSensorActivationStatus(sensor, true);
        verify(securityRepository, times(1)).setAlarmStatus(eq(AlarmStatus.PENDING_ALARM));
    }

    @Test
    @Order(2)
    @DisplayName(
            "2. If alarm is armed and a sensor becomes activated and the system is already pending alarm, " +
                    "set the alarm status to alarm.")
    public void alarmArmed_alarmPending_sensorActivated_alarmActive() {
        when(sensor.getActive()).thenReturn(false);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        securityService.changeSensorActivationStatus(sensor, true);
        verify(securityRepository, times(1)).setAlarmStatus(eq(AlarmStatus.ALARM));
    }

    @Test
    @Order(3)
    @DisplayName("3. If pending alarm and all sensors are inactive, return to no alarm state.")
    public void pendingAlarm_sensorInactive_noAlarmActive() {
        when(sensor.getActive()).thenReturn(true);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        securityService.changeSensorActivationStatus(sensor, false);
        verify(securityRepository, times(1)).setAlarmStatus(eq(AlarmStatus.NO_ALARM));
    }

    @Test
    @Order(4)
    @DisplayName("4. If alarm is active, change in sensor state should not affect the alarm state.")
    public void alarmActive_sensorChange_notAffectAlarmState() {
        when(sensor.getActive()).thenReturn(true);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.ALARM);
        securityService.changeSensorActivationStatus(sensor, false);
        verify(securityRepository, times(0)).setAlarmStatus(any());
    }

    @Test
    @Order(5)
    @DisplayName("5. If a sensor is activated while already active and the system is in pending state, " +
            "change it to alarm state.")
    public void pendingAlarm_additionalSensorActivated_alarmIsActive() {
        when(sensor.getActive()).thenReturn(true);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        securityService.changeSensorActivationStatus(sensor, true);
        verify(securityRepository, times(1)).setAlarmStatus(eq(AlarmStatus.ALARM));
    }

    @Test
    @Order(6)
    @DisplayName("6. If a sensor is deactivated while already inactive, make no changes to the alarm state.")
    public void noActiveSensor_sensorDeactivated_noAlarmChange() {
        when(sensor.getActive()).thenReturn(false);
        securityService.changeSensorActivationStatus(sensor, false);
        verify(securityRepository, times(0)).setAlarmStatus(any());
    }

    @Test
    @Order(7)
    @DisplayName("7. If the image service identifies an image containing a cat while the system is armed-home," +
            "put the system into alarm status.")
    public void armedAtHome_catDetected_alarmActive() {
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        when(imageService.imageContainsCat(null, 50.0f)).thenReturn(true);
        securityService.processImage(null);
        verify(securityRepository, times(1)).setAlarmStatus(eq(AlarmStatus.ALARM));
    }

    @Test
    @Order(8)
    @DisplayName("8. If the image service identifies an image that does not contain a cat, " +
            "change the status to no alarm as long as the sensors are not active.")
    public void noCatDetected_alarmNotActive() {
        when(imageService.imageContainsCat(null, 50.0f)).thenReturn(false);
        securityService.changeSensorActivationStatus(sensor, false);
        securityService.processImage(null);
        verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    @Test
    @Order(9)
    @DisplayName("9. If the system is disarmed, set the status to no alarm.")
    public void systemDisarmed_noAlarm() {
        securityService.setArmingStatus(ArmingStatus.DISARMED);
        verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    @ParameterizedTest
    @MethodSource("systemArmingStatusProvider")
    @Order(10)
    @DisplayName("10. If the system is armed, reset all sensors to inactive.")
    public void systemArmed_resetSensorsInactive(ArmingStatus armingStatus) {
        securityService.setArmingStatus(ArmingStatus.DISARMED);
        Set<Sensor> sensors = getSensors(true, 4);
        when(securityRepository.getSensors()).thenReturn(sensors);
        securityService.setArmingStatus(armingStatus);
        List<Executable> sensorList = new ArrayList<>();
        sensors.forEach(sensor -> sensorList.add(() -> assertEquals(sensor.getActive(), false)));
        assertAll(sensorList);
    }

    private static Stream<Arguments> systemArmingStatusProvider() {
        return Stream.of(
                Arguments.of(ArmingStatus.ARMED_HOME),
                Arguments.of(ArmingStatus.ARMED_AWAY)
        );
    }

    @Test
    @Order(11)
    @DisplayName("11. If the camera shows a cat while system is armed-home set the alarm status to alarm.")
    public void armedHome_whileCatDetected_alarmActive() {
        when(imageService.imageContainsCat(null, 50.0f)).thenReturn(true);
        securityService.processImage(null);
        securityService.setArmingStatus(ArmingStatus.ARMED_HOME);
        verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.ALARM);
        securityService.setArmingStatus(ArmingStatus.ARMED_AWAY);
        securityService.changeSensorActivationStatus(sensor, false);
        verify(securityRepository, times(1)).setAlarmStatus(AlarmStatus.NO_ALARM);
    }
}
