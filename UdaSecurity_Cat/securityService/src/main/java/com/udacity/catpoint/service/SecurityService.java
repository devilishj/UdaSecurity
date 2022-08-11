package com.udacity.catpoint.service;


import com.udacity.catpoint.application.SensorLabelListener;
import com.udacity.catpoint.application.StatusListener;
import com.udacity.catpoint.data.AlarmStatus;
import com.udacity.catpoint.data.ArmingStatus;
import com.udacity.catpoint.data.SecurityRepository;
import com.udacity.catpoint.data.Sensor;
import com.udacity.catpoint.imageServices.AwsImageService;

import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * Service that receives information about changes to the security system. Responsible for
 * forwarding updates to the repository and making any decisions about changing the system state.
 *
 * This is the class that should contain most of the business logic for our system, and it is the
 * class you will be writing unit tests for.
 */
public final class SecurityService {

    private final AwsImageService imageService;
    private final SecurityRepository securityRepository;
    private final Set<StatusListener> statusListeners = new HashSet<>();
    private final Set<SensorLabelListener> sensorLabelListeners = new HashSet<>();
    private boolean isCat = false;
    private boolean currentlyDisarmed = false;

    public SecurityService(SecurityRepository securityRepository, AwsImageService imageService) {
        this.securityRepository = securityRepository;
        this.imageService = imageService;
    }

    /**
     * Sets the current arming status for the system. Changing the arming status
     * may update both the alarm status.
     * @param armingStatus
     */
    public void setArmingStatus(ArmingStatus armingStatus) {
        ConcurrentSkipListSet<Sensor> sensors = new ConcurrentSkipListSet<>(getSensors());

        if(armingStatus == ArmingStatus.ARMED_HOME) {
            if(isCat) {
                setAlarmStatus(AlarmStatus.ALARM);
            }

            if(currentlyDisarmed) {
                sensors.forEach(sensor -> changeSensorActivationStatus(sensor, false));
                currentlyDisarmed = false;
                sensorLabelListeners.forEach(sb -> sb.notify(armingStatus));
            }
        }
        else if(armingStatus == ArmingStatus.ARMED_AWAY) {
            if(allSensorsInactive()) {
                setAlarmStatus(AlarmStatus.NO_ALARM);
            }
            if(currentlyDisarmed) {
                sensors.forEach(sensor -> changeSensorActivationStatus(sensor, false));
                currentlyDisarmed = false;
                setAlarmStatus(AlarmStatus.NO_ALARM);
                sensorLabelListeners.forEach(sb -> sb.notify(armingStatus));
            }
        }
        else if(armingStatus == ArmingStatus.DISARMED) {
            setAlarmStatus(AlarmStatus.NO_ALARM);
            currentlyDisarmed = true;

        }
        securityRepository.setArmingStatus(armingStatus);
        statusListeners.forEach(StatusListener::sensorStatusChanged);
    }

    /**
     * Internal method that handles alarm status changes based on whether
     * the camera currently shows a cat.
     * @param cat True if a cat is detected, otherwise false.
     */
    private void catDetected(Boolean cat) {
        isCat = cat;
        if(cat && getArmingStatus() == ArmingStatus.ARMED_HOME) {
                setAlarmStatus(AlarmStatus.ALARM);
        } else
        if (!cat && allSensorsInactive()){
            setAlarmStatus(AlarmStatus.NO_ALARM);
        }
        statusListeners.forEach(sl -> sl.catDetected(cat));

    }

    /**
     * Register the StatusListener for alarm system updates from within the SecurityService.
     * @param statusListener
     */
    public void addStatusListener(StatusListener statusListener) {
        statusListeners.add(statusListener);
    }

    public void addSensorLabelListener(SensorLabelListener statusListener) {
        sensorLabelListeners.add(statusListener);
    }

    /**
     * Change the alarm status of the system and notify all listeners.
     * @param status
     */
    public void setAlarmStatus(AlarmStatus status) {
        securityRepository.setAlarmStatus(status);
        statusListeners.forEach(sl -> sl.notify(status));

    }

    /**
     * Internal method for updating the alarm status when a sensor has been activated.
     */
    private void handleSensorActivated() {
        if(securityRepository.getArmingStatus() == ArmingStatus.DISARMED) {
            return; //no problem if the system is disarmed
        }  else if (securityRepository.getAlarmStatus() == AlarmStatus.NO_ALARM){
            setAlarmStatus(AlarmStatus.PENDING_ALARM);
        } else if (securityRepository.getAlarmStatus() == AlarmStatus.PENDING_ALARM){
            setAlarmStatus(AlarmStatus.ALARM);
        }
    }

    /**
     * Internal method for updating the alarm status when a sensor has been deactivated
     */
    private void handleSensorDeactivated() {
        if(securityRepository.getAlarmStatus() == (AlarmStatus.ALARM)) {
            return;
        }
        else if(securityRepository.getAlarmStatus() == (AlarmStatus.PENDING_ALARM)) {
            setAlarmStatus(AlarmStatus.NO_ALARM);
        }
    }

    /**
     * Change the activation status for the specified sensor and update alarm status if necessary.
     * @param sensor
     * @param active
     */
    public void changeSensorActivationStatus(Sensor sensor, Boolean active) {
        if(!sensor.getActive() && active) {
            handleSensorActivated();
        } else if (sensor.getActive() && !active) {
            handleSensorDeactivated();
        } else if(sensor.getActive() && active) {
            handleSensorActivated();
        }
        sensor.setActive(active);
        securityRepository.updateSensor(sensor);
    }

    private boolean allSensorsInactive() {
        return getSensors()
                .stream()
                .noneMatch(Sensor::getActive);
    }

    /**
     * Send an image to the SecurityService for processing. The securityService will use its provided
     * ImageService to analyze the image for cats and update the alarm status accordingly.
     * @param currentCameraImage
     */
    public void processImage(BufferedImage currentCameraImage) {
        catDetected(imageService.imageContainsCat(currentCameraImage, 50.0f));
    }

    public AlarmStatus getAlarmStatus() {
        return securityRepository.getAlarmStatus();
    }

    public Set<Sensor> getSensors() {
        return securityRepository.getSensors();
    }

    public void addSensor(Sensor sensor) {
        securityRepository.addSensor(sensor);
    }

    public void removeSensor(Sensor sensor) {
        securityRepository.removeSensor(sensor);
    }

    public ArmingStatus getArmingStatus() {
        return securityRepository.getArmingStatus();
    }
}
