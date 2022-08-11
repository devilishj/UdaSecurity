package com.udacity.catpoint.application;

import com.udacity.catpoint.data.AlarmStatus;
import com.udacity.catpoint.data.ArmingStatus;

public interface SensorLabelListener {
    void notify(ArmingStatus status);
}
