package org.firstinspires.ftc.Robot1;

import android.graphics.Color;
import android.util.Log;

import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.hardware.ColorSensor;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.util.ElapsedTime;
import com.qualcomm.robotcore.util.Range;

import org.firstinspires.ftc.robotcore.external.ClassFactory;
import org.firstinspires.ftc.robotcore.external.matrices.OpenGLMatrix;
import org.firstinspires.ftc.robotcore.external.matrices.VectorF;
import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit;
import org.firstinspires.ftc.robotcore.external.navigation.AxesOrder;
import org.firstinspires.ftc.robotcore.external.navigation.AxesReference;
import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit;
import org.firstinspires.ftc.robotcore.external.navigation.Orientation;

import static java.lang.Math.abs;
import static java.lang.Math.max;
import static org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit.mmPerInch;

import com.vuforia.HINT;
import com.vuforia.Vuforia;
import org.firstinspires.ftc.robotcore.external.navigation.VuforiaLocalizer;
import org.firstinspires.ftc.robotcore.external.navigation.VuforiaTrackable;
import org.firstinspires.ftc.robotcore.external.navigation.VuforiaTrackableDefaultListener;
import org.firstinspires.ftc.robotcore.external.navigation.VuforiaTrackables;

public class FTC11138Base1 extends LinearOpMode {

    static final double COUNTS_PER_MOTOR_REV = 1680;    // Neverest 60 motor
    static final double DRIVE_GEAR_REDUCTION = 0.5;     // Robot has another 1:2 gear. This is < 1.0 if geared UP
    static final double WHEEL_DIAMETER_INCHES = 4.0;     // For figuring circumference
    static final double COUNTS_PER_INCH = (COUNTS_PER_MOTOR_REV * DRIVE_GEAR_REDUCTION) / (WHEEL_DIAMETER_INCHES * Math.PI);
    static final int LED_CHANNEL = 5;
    //ToDo: The follow parameters need to be tested and adjusted, but applies to all running opmode.

    float Kp_Drive = 0.01f;  //coefficient to adjust drive straight left and right motor power.
    float stopDistanceMM = 103;
    static final float robotWidthInch = 18;
    static final float robotLengthInch = 18;
    static final float playFieldWidthInch = 141;
    private ElapsedTime runtime = new ElapsedTime();
    FTC11138Robot1 robot = new FTC11138Robot1();

    Orientation imuAngles;
    static double DRIVE_SPEED;
    static double SHOOT_SPEED;
    static double TURN_SPEED;
    double refPositionAngle;
    double currentPositionAngle;
    double startPositionAngle; //this is the angle when op mode starts
    double groundColorValue;

    VuforiaLocalizer myVuforiaLocalizer;
    VuforiaTrackables beaconTargets;
    VuforiaTrackable singleBeaconTarget;
    VuforiaTrackableDefaultListener listener;
    VuforiaLocalizer.Parameters params;

    @Override
    public void runOpMode() throws InterruptedException {
    }

    public void getInitValues() {
        startPositionAngle = getIMUAngle();
        groundColorValue = getHSVValue(robot.floorColorSensor);
        telemetry.addData("Start drive direction", startPositionAngle);
        telemetry.addData("Ground color value", groundColorValue);
        telemetry.update();
    }

    public double getHSVValue(ColorSensor colorSensorToUse) {
        int redValue = colorSensorToUse.red();
        int blueValue = colorSensorToUse.blue();
        int greenValue = colorSensorToUse.green();

        float hsvValues[] = {0F, 0F, 0F};

        Color.RGBToHSV(redValue, greenValue, blueValue, hsvValues);
        return hsvValues[2];
    }

    public float getIMUAngle() {
        float angleRead;
        imuAngles = robot.imu.getAngularOrientation().toAxesReference(AxesReference.INTRINSIC).toAxesOrder(AxesOrder.ZYX);
        angleRead = imuAngles.firstAngle;
        if (angleRead > 180)
            angleRead -= 360;
        if (angleRead <= -180)
            angleRead += 360;
        return (angleRead);
    }

    public DetectedColor detectedBeaconColor(ColorSensor colorSensorToUse) {
        DetectedColor sensorReadColor;

        int redValue = colorSensorToUse.red();
        int blueValue = colorSensorToUse.blue();

        if (redValue > blueValue)
            sensorReadColor = DetectedColor.Red;
        else sensorReadColor = DetectedColor.Blue;

        return sensorReadColor;
    }


    public DetectedColor detectedGroundColor(ColorSensor colorSensorToUse) {
        DetectedColor sensorReadColor;

        int redValue = colorSensorToUse.red();
        int blueValue = colorSensorToUse.blue();
        int greenValue = colorSensorToUse.green();


        if (redValue > 3 && blueValue > 3 && greenValue > 3)
            sensorReadColor = DetectedColor.White;
        else
            sensorReadColor = DetectedColor.Black;

        return sensorReadColor;
    }

    public void turnRobot(double turnSpeed, Direction turnDirection, double turnDegree, double timeoutSec) {

        //imu.startAccelerationIntegration(new Position(), new Velocity(), 1000);
        refPositionAngle = getIMUAngle();
        currentPositionAngle = refPositionAngle; //At the start, current position is the ref position.
        double turnPower;

        turnPower = turnSpeed;
        robot.leftMotor.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
        robot.rightMotor.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
        sleep(20);

        //if the turnDegree is less than 0, meaning need to turn to the opposite direction of the passed in direction parameter
        if (turnDegree < 0) {
            if (turnDirection == Direction.Left) turnDirection = Direction.Right;
            else turnDirection = Direction.Right;
            turnDegree = abs(turnDegree); //use abs value to control the robot rotation
        }

        runtime.reset();
        while (abs(getError(refPositionAngle, currentPositionAngle)) < turnDegree && runtime.seconds() < timeoutSec) {
            if (abs(getError(refPositionAngle, currentPositionAngle)) > (turnDegree - 7)) {
                //reduce turn speed when within angle tolerance
                turnPower = turnSpeed * 1;
                stopMotor();
            }

            if (turnDirection == Direction.Left) {
                robot.leftMotor.setPower(-turnPower);
                robot.rightMotor.setPower(turnPower);
            } else {
                robot.leftMotor.setPower(turnPower);
                robot.rightMotor.setPower(-turnPower);
            }

            //get the angle reading at current position
            currentPositionAngle = getIMUAngle();
        }
        stopMotor();
    }

    public void turnRobotRightWheelMorePower(double turnSpeed, Direction turnDirection, double turnDegree, double timeoutSec) {

        //imu.startAccelerationIntegration(new Position(), new Velocity(), 1000);
        refPositionAngle = getIMUAngle();
        currentPositionAngle = refPositionAngle; //At the start, current position is the ref position.
        double turnPower;

        turnPower = turnSpeed;
        robot.leftMotor.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
        robot.rightMotor.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
        sleep(20);

        //if the turnDegree is less than 0, meaning need to turn to the opposite direction of the passed in direction parameter
        if (turnDegree < 0) {
            if (turnDirection == Direction.Left) turnDirection = Direction.Right;
            else turnDirection = Direction.Right;
            turnDegree = abs(turnDegree); //use abs value to control the robot rotation
        }

        runtime.reset();
        while (abs(getError(refPositionAngle, currentPositionAngle)) < turnDegree && runtime.seconds() < timeoutSec) {
            if (abs(getError(refPositionAngle, currentPositionAngle)) > (turnDegree - 7)) {
                //reduce turn speed when within angle tolerance
                turnPower = turnSpeed * 1;
                stopMotor();
            }

            if (turnDirection == Direction.Left) {
                robot.leftMotor.setPower(-turnPower);
                robot.rightMotor.setPower(turnPower + 0.03);
            } else {
                robot.leftMotor.setPower(turnPower);
                robot.rightMotor.setPower(-turnPower - 0.03);
            }

            //get the angle reading at current position
            currentPositionAngle = getIMUAngle();
        }
        stopMotor();
    }

    public void turnRobot1Wheel(double turnSpeed, Direction turnDirection, double turnDegree, double timeoutSec) {

        refPositionAngle = getIMUAngle();
        currentPositionAngle = refPositionAngle; //At the start, current position is the ref position.
        double turnPower;
        double turnPowerIncrease = 0.15;  //Todo: 1 wheel drive need more power

        robot.leftMotor.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
        robot.rightMotor.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
        sleep(20);

        turnPower = turnSpeed > 0 ? turnSpeed + turnPowerIncrease : turnSpeed - turnPowerIncrease; //turnSpeed should be positive from the calling routine, but just in case it's negative

        //if the turnDegree is less than 0, meaning need to turn to the opposite direction of the passed in direction parameter
        if (turnDegree < 0) {
            if (turnDirection == Direction.Left) turnDirection = Direction.Right;
            else turnDirection = Direction.Right;
            turnDegree = abs(turnDegree); //use abs value to control the robot rotation
        }

        runtime.reset();
        while (abs(getError(refPositionAngle, currentPositionAngle)) < turnDegree && runtime.seconds() < timeoutSec) {
            if (abs(getError(refPositionAngle, currentPositionAngle)) > (turnDegree - 10)) {
                //reduce turn speed when within angle tolerance
                turnPower = turnSpeed * 1;
                stopMotor();
                sleep(100); //wait for short time, make sure IMU gets new position reading.
            }

            if (turnDirection == Direction.Left) {
                robot.rightMotor.setPower(turnPower);
            } else {
                robot.leftMotor.setPower(turnPower);
            }

            //get the angle reading at current position
            currentPositionAngle = getIMUAngle();
        }
        stopMotor();
    }

    public void encoderDrive(double speed, double leftInches, double rightInches, double timeoutS) {
        int newLeftMotorTarget;
        int newRightMotorTarget;
        ElapsedTime driveTimer = new ElapsedTime(); //define a new timer just for the encoder drive, because functions calls encoderDrive has another runtimer

        robot.leftMotor.setMode(DcMotor.RunMode.RUN_USING_ENCODER);
        robot.rightMotor.setMode(DcMotor.RunMode.RUN_USING_ENCODER);
        sleep(20);

        // Ensure that the opmode is still active
        if (opModeIsActive()) {

            // Determine new target position, and pass to motor controller
            newLeftMotorTarget = robot.leftMotor.getCurrentPosition() + (int) (leftInches * COUNTS_PER_INCH);
            newRightMotorTarget = robot.rightMotor.getCurrentPosition() + (int) (rightInches * COUNTS_PER_INCH);
            robot.leftMotor.setTargetPosition(newLeftMotorTarget);
            robot.rightMotor.setTargetPosition(newRightMotorTarget);
            sleep(20);
            // Turn On RUN_TO_POSITION
            robot.leftMotor.setMode(DcMotor.RunMode.RUN_TO_POSITION);
            robot.rightMotor.setMode(DcMotor.RunMode.RUN_TO_POSITION);
            sleep(20);

            // reset the timeout time and start motion.
            driveTimer.reset();
            robot.leftMotor.setPower(abs(speed));
            robot.rightMotor.setPower(abs(speed));

            // keep looping while we are still active, and there is time left, and both motors are running.
            while (opModeIsActive() &&
                    (driveTimer.seconds() < timeoutS) &&
                    ((robot.leftMotor.isBusy() && robot.rightMotor.isBusy()))) {

                // Display it for the driver.
                telemetry.addData("Path1", "Running to %7d :%7d", newLeftMotorTarget, newRightMotorTarget);
                telemetry.addData("Path2", "Running at %7d :%7d",
                        robot.leftMotor.getCurrentPosition(),
                        robot.rightMotor.getCurrentPosition());
                telemetry.update();
            }
        }
    }

    public void noEncoderDetectWhiteLine() {
        robot.leftMotor.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
        robot.rightMotor.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
        sleep(20); //give time to set mode
        while (!detectWhiteLine()) {
            robot.rightMotor.setPower(0.239);
            robot.leftMotor.setPower(0.239);
        }
        stopMotor();
    }

    public boolean detectWhiteLine() {
        boolean whiteLine = (detectedGroundColor(robot.floorColorSensor) == DetectedColor.White);
        return whiteLine;
    }

    public void loadBall() {
        double startingPosition = robot.dispensingServo.getPosition();
        telemetry.addData("Servo Starting Position", startingPosition);
        telemetry.update();

        double nextPosition = 1.0;
        robot.dispensingServo.setPosition(nextPosition);
        telemetry.addData("Next position", nextPosition);
        telemetry.update();
        sleep(800);

        double homePosition = 0.15;
        robot.dispensingServo.setPosition(homePosition);
        telemetry.addData("Home Position", homePosition);
        telemetry.update();
    }

    public void shoot() {
        //robot.rightShoot.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
        //sleep(20);
        robot.rightShoot.setPower(0.3);
        sleep(800);
        robot.rightShoot.setPower(0.0);
    }

    public void shoot2() {
        //robot.rightShoot.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
        //sleep(20);
        robot.rightShoot.setPower(0.3);
        sleep(300);
        robot.rightShoot.setPower(0.0);
    }

    public void approachTouch() {
        Log.i("*ALEX*", "Robot has entered approachTouch function");
        //double TURN_SPEED = 0.23; //working speed
        double TURN_SPEED = 0.22;
        robot.rightShoot.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
        sleep(20);
        Log.i("*ALEX*", "Right shoot will now start turning");
        robot.rightShoot.setPower(TURN_SPEED);
        Log.i("*ALEX*", "Right shoot has started rotating");
        while (!robot.shootTouchSensor.isPressed()) {
            sleep(1);
        }
        Log.i("*ALEX*", "Right Shoot has touched touch sensor and will now stop.");
        robot.rightShoot.setPower(0);
        Log.i("*ALEX*", "Right shoot has stopped");
    }

    public void shootTwoParticles() {
        Log.i("*ALEX*", "Robot has entered shootTwoParticles function");
        approachTouch();
        Log.i("*ALEX*", "Right shoot has finished first approachTouch");
        sleep(100);
        Log.i("*ALEX*", "Right shoot will start to shoot first time");
        shoot();
        Log.i("*ALEX*", "Right shoot has finished first shot");
        sleep(200);
        Log.i("*ALEX*", "Right shoot will get into second approachTouch");
        approachTouch();
        sleep(100);
        Log.i("*ALEX*", "Right shoot has finished second approachTouch");
        loadBall();
        Log.i("*ALEX*", "Right shoot has finished loadBall function");
        sleep(900);
        Log.i("*ALEX*", "Right shoot will get into second shoot function");
        shoot2();
        Log.i("*ALEX*", "Right shoot has finished second shoot function");
        //sleep(500);
    }

    public void freeMotorDrive(double leftSpeed, double rightSpeed) {

        // Ensure that the opmode is still active
        if (opModeIsActive()) {

            robot.leftMotor.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
            robot.rightMotor.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
            sleep(20);
            //start to run the motor
            robot.leftMotor.setPower(leftSpeed);
            robot.rightMotor.setPower(rightSpeed);

        } else {
            // Stop all motion;
            stopMotor();
        }
    }

    public void stopMotor() {
        robot.leftMotor.setPower(0);
        robot.rightMotor.setPower(0);
    }

    public void adjustMotorPower(double drivePower, double expectedValue, double feedbackValue, double scalingFactor) {

        double error;
        double steer;

        error = getError(expectedValue, feedbackValue);
        steer = getSteer(error, scalingFactor);

        //ToDo: need to confirm powerToAdjust polarity
        double newLeftMotorPower = drivePower - steer;
        double newRightMotorPower = drivePower + steer;

        double maxPower = max(abs(newLeftMotorPower), abs(newRightMotorPower));

        if (maxPower > 1) {
            newLeftMotorPower /= maxPower;
            newRightMotorPower /= maxPower;
        }

        robot.leftMotor.setPower(newLeftMotorPower);
        robot.rightMotor.setPower(newRightMotorPower);
    }

    public double calcAngleToTurn(double ref, double angleToRef, Direction turnDirection) {
        double angle;
        double delta;
        double current;

        current = getIMUAngle();
        delta = getError(ref, current);

        if (turnDirection == Direction.Right)
            angle = angleToRef - delta;
        else
            angle = angleToRef + delta;

        if (angle > 180) angle -= 360;
        if (angle <= -180) angle += 360;

        //ToDo: if angle<0, meaning current position already passed the target position, need to reverse the turn direction.
        return (angle);
    }

    private double getError(double refAngle, double currentAngle) {

        double robotError;

        // calculate error in -179 to +180 range, - indicate current to the left of ref. + indicate current to the right of ref
        robotError = refAngle - currentAngle;
        if (robotError > 180) robotError -= 360;
        if (robotError <= -180) robotError += 360;
        return robotError;
    }

    private double getSteer(double error, double PCoeff) {
        return Range.clip(error * PCoeff, -1, 1);
    }

    public void driveAlongTape(double drivePower, DetectedColor tapeColor, Direction tapeEdge) {
        double newLeftMotorPower;
        double newRightMotorPower;
        double powerToAdjust = (tapeEdge == Direction.Left) ? 0.02 : -0.02; //ToDo: need to confirm powerToAdjust polarity

        if (detectedGroundColor(robot.floorColorSensor) == tapeColor) {

            newLeftMotorPower = drivePower - powerToAdjust;
            newRightMotorPower = drivePower + powerToAdjust;
        } else {
            newLeftMotorPower = drivePower + powerToAdjust;
            newRightMotorPower = drivePower - powerToAdjust;
        }

        double maxPower = max(abs(newLeftMotorPower), abs(newRightMotorPower));

        if (maxPower > 1) {
            newLeftMotorPower /= maxPower;
            newRightMotorPower /= maxPower;
        }

        robot.leftMotor.setPower(newLeftMotorPower);
        robot.rightMotor.setPower(newRightMotorPower);

        telemetry.addData("Detected Tape Color", detectedGroundColor(robot.floorColorSensor));
        telemetry.addData("left motor power", newLeftMotorPower);
        telemetry.addData("right motor power", newRightMotorPower);
        telemetry.update();
    }

    public void moveFrom1stTo2ndBeacon(boolean isRedTeam) {
        DRIVE_SPEED = 0.285;
        //TURN_SPEED = 0.305; //working
        TURN_SPEED = 0.31;

        Direction turnDirection = isRedTeam ? Direction.Left : Direction.Left;
        encoderDrive(DRIVE_SPEED, -5, -5, 10.0); //move out a little more\
        double turnDegree = calcAngleToTurn(startPositionAngle, 2, turnDirection); //working
        //double turnDegree = calcAngleToTurn(startPositionAngle, 3, turnDirection); //so it can move forward parallel to wall
        //turnRobot(TURN_SPEED, turnDirection, turnDegree, 7); //turn robot 90 degrees to the right, needs slightly more power
        turnRobotRightWheelMorePower(TURN_SPEED, turnDirection, turnDegree, 7);
        sleep(100);
        encoderDrive(0.39, 42, 42, 10.0); //let robot get out of first white line zone
        sleep(100);
        noEncoderDetectWhiteLine();
        sleep(100);
        encoderDrive(DRIVE_SPEED, 1.5, 1.5, 10.0);

        turnDegree = calcAngleToTurn(startPositionAngle, 80, turnDirection);
        //turnRobot(0.33, turnDirection, turnDegree, 7); //turn left 90 degrees towards the beacon
        //turnRobotRightWheelMorePower(0.33, turnDirection, turnDegree, 7);
        turnRobotRightWheelMorePower(0.34, turnDirection, turnDegree, 7);
    }

    public void moveFrom1stTo2ndBeaconBlue() {
        DRIVE_SPEED = 0.285;
        TURN_SPEED = 0.305;
        Direction turnDirection = Direction.Left;
        encoderDrive(DRIVE_SPEED, -5, -5, 10.0); //move out a little more\
        double turnDegree = calcAngleToTurn(startPositionAngle, -6, turnDirection); //working
        //double turnDegree = calcAngleToTurn(startPositionAngle, 3, turnDirection); //so it can move forward parallel to wall
        //turnRobot(TURN_SPEED, turnDirection, turnDegree, 7); //turn robot 90 degrees to the right, needs slightly more power
        turnRobotRightWheelMorePower(TURN_SPEED, turnDirection, turnDegree, 7);
        sleep(100);
        encoderDrive(0.36, 42, 42, 10.0); //let robot get out of first white line zone
        sleep(100);
        noEncoderDetectWhiteLine();
        encoderDrive(DRIVE_SPEED, 0.5, 0.5, 10.0);
        turnDirection = Direction.Right;
        turnDegree = calcAngleToTurn(startPositionAngle, 80, turnDirection);
        //turnRobot(0.33, turnDirection, turnDegree, 7); //turn left 90 degrees towards the beacon
        //turnRobotRightWheelMorePower(0.34, turnDirection, turnDegree, 7);
        turnRobot(0.36, turnDirection, turnDegree, 7);

    }

    public void backUpFromSecondBeaconAndShootRed() {
        Direction turnDirection = Direction.Left;
        DRIVE_SPEED = 0.35;
        encoderDrive(DRIVE_SPEED, -8, -8, 10.0); //test
        TURN_SPEED = 0.27;
        TURN_SPEED = 0.32;
        //double turnDegree = calcAngleToTurn(startPositionAngle, 45, turnDirection); //working
        double turnDegree = calcAngleToTurn(startPositionAngle, 42, turnDirection); //test turn a little more
        //turnRobot(TURN_SPEED, turnDirection, turnDegree, 10.0); //tilt robot
        turnRobotRightWheelMorePower(TURN_SPEED, turnDirection, turnDegree, 10.0);
        //encoderDrive(DRIVE_SPEED, -2, -2, 10.0);
        encoderDrive(DRIVE_SPEED, -4, -4, 10.0); //try back out a little more
        stopMotor();
        shootTwoParticles();
    }

    public void backUpAndPushSecondBeaconBlue() {
        DRIVE_SPEED = 0.285;
        TURN_SPEED = 0.245;
        Direction turnDirection = Direction.Left;
        encoderDrive(DRIVE_SPEED, 0, -1, 10.0); //re-align robot
        sleep(100);
        encoderDrive(DRIVE_SPEED, -5, -5, 10.0); //move out a little more
        double turnDegree = calcAngleToTurn(startPositionAngle, 0, turnDirection); //so it can move forward parallel to wall
        turnRobot1Wheel(0.26, turnDirection, turnDegree, 7); //turn robot 90 degrees to the right, needs slightly more power
        sleep(100);
        /*encoderDrive(0.275, 35, 35, 10.0); //let robot get out of first white line zone
        sleep(100);*/
        noEncoderDetectWhiteLine();

        turnDirection = Direction.Right;
        turnDegree = calcAngleToTurn(startPositionAngle, 88, turnDirection);
        turnRobot(TURN_SPEED, turnDirection, turnDegree, 7);//turn left 88 degrees towards the beacon
        finalMoveToFrontBeacon();
        pushFrontBeacon(DetectedColor.Blue); //push red button
    }

    public void backUpFromSecondBeaconAndShootBlue() {
        Direction turnDirection = Direction.Left;
        DRIVE_SPEED = 0.35;
        encoderDrive(DRIVE_SPEED, -8, -8, 10.0); // move back quickly
        TURN_SPEED = 0.31;
        double turnDegree = calcAngleToTurn(startPositionAngle, -54, turnDirection);
        turnRobot(TURN_SPEED, turnDirection, turnDegree, 10.0); //tilt robot
        encoderDrive(DRIVE_SPEED, -6.5, -6.5, 10.0);
        stopMotor();
        shootTwoParticles();
    }

    public void startFromClosePositionTo1stBeacon(boolean isRedTeam) {
        DRIVE_SPEED = 0.31;
        //TURN_SPEED = 0.264; //ToDo: adjust for the actual robot //working
        TURN_SPEED = 0.268;

        Direction turnDirection = isRedTeam ? Direction.Left : Direction.Right;

        encoderDrive(DRIVE_SPEED, 11, 11, 10.0); //move out a little to save time
        sleep(100); //give time to complete task

        double turnDegree = calcAngleToTurn(startPositionAngle, 45, turnDirection);
        //turnRobot(TURN_SPEED, turnDirection, turnDegree, 7);
        turnRobotRightWheelMorePower(TURN_SPEED, turnDirection, turnDegree, 7);
        sleep(100);
        encoderDrive(0.38, 40, 40, 10.0);
        sleep(100);
        noEncoderDetectWhiteLine();
        sleep(100);

        turnDegree = calcAngleToTurn(startPositionAngle, 88, turnDirection); //robot overturns a little so don't rotate an exact 90
        //turnRobot(TURN_SPEED, turnDirection, turnDegree, 7); //make very precise turn
        turnRobotRightWheelMorePower(TURN_SPEED, turnDirection, turnDegree, 7);
    }

    public void startFromClosePositionTo1stBeaconBlue() {
        DRIVE_SPEED = 0.31;
        //TURN_SPEED = 0.27; //ToDo: adjust for the actual robot
        TURN_SPEED = 0.29;

        Direction turnDirection = Direction.Right;

        encoderDrive(DRIVE_SPEED, 11, 11, 10.0); //move out a little to save time
        sleep(100); //give time to complete task

        double turnDegree = calcAngleToTurn(startPositionAngle, 43, turnDirection);
        //turnRobot(TURN_SPEED, turnDirection, turnDegree, 7);
        turnRobotRightWheelMorePower(TURN_SPEED, turnDirection, turnDegree, 7);
        sleep(100);
        encoderDrive(0.38, 40, 40, 10.0);
        sleep(100);
        noEncoderDetectWhiteLine();
        sleep(100);

        turnDegree = calcAngleToTurn(startPositionAngle, 88, turnDirection); //robot overturns a little so don't rotate an exact 90
        //turnRobot(TURN_SPEED, turnDirection, turnDegree, 7); //make very precise turn
        turnRobotRightWheelMorePower(TURN_SPEED, turnDirection, turnDegree, 7);
    }

    public void startFromFarPositionTo1stBeacon(boolean isRedTeam) {
        double turnDegree;
        Direction turnDirection;

        DRIVE_SPEED = 0.30;
        TURN_SPEED = 0.21;

        turnDegree = 57;
        turnDirection = isRedTeam ? Direction.Left : Direction.Right;
        turnRobot1Wheel(TURN_SPEED, turnDirection, turnDegree, 10);//turn robot towards wall

        encoderDrive(DRIVE_SPEED, 75, 75, 12.0); //drive some distance

        noEncoderDetectWhiteLine();
        sleep(100);

        turnDegree = calcAngleToTurn(startPositionAngle, 90, turnDirection); //turn robot so that beacon is in front of it
        turnRobot1Wheel(TURN_SPEED, turnDirection, turnDegree, 7);
    }

    public void startFromClosePositionToParallelBeaconWall(boolean isRedTeam) {
        double turnDegree;
        Direction turnDirection;

        DRIVE_SPEED = 0.3;
        TURN_SPEED = 0.29;

        encoderDrive(0.25, 3, 3, 10.0);
        turnDirection = isRedTeam ? Direction.Left : Direction.Right;
        turnRobot1Wheel(TURN_SPEED, turnDirection, 45, 10);//turn robot towards wall

        encoderDrive(DRIVE_SPEED, 40, 40, 10.0); //drive some distance

        noEncoderDetectWhiteLine();
        sleep(100);

        turnDirection = isRedTeam ? Direction.Right : Direction.Left; //turn so that robot is parallel to starting position angle
        turnDegree = calcAngleToTurn(startPositionAngle, 0, turnDirection);
        turnRobot(TURN_SPEED, turnDirection, turnDegree, 7);
    }

    public void startFromFarPositionToParallelBeaconWall(boolean isRedTeam) {
        double turnDegree;
        Direction turnDirection;

        DRIVE_SPEED = 0.3;
        TURN_SPEED = 0.21;

        turnDegree = 57;
        turnDirection = isRedTeam ? Direction.Left : Direction.Right;
        turnRobot1Wheel(TURN_SPEED, turnDirection, turnDegree, 10);//turn robot towards wall

        encoderDrive(DRIVE_SPEED, 75, 75, 12.0); //drive some distance

        noEncoderDetectWhiteLine();
        sleep(100);

        turnDegree = calcAngleToTurn(startPositionAngle, 0, turnDirection); //turn robot so that beacon is in front of it
        turnRobot1Wheel(TURN_SPEED, turnDirection, turnDegree, 7);
    }

    public void finalMoveToFrontBeacon() {
        freeMotorDrive(0.159, 0.159); //move to beacon at reduced speed
        while (robot.rangeSensor.getDistance(DistanceUnit.MM) > stopDistanceMM) {
            sleep(1);
        }
        stopMotor();
    }

    public void pushFrontBeacon(DetectedColor beaconColorToPush) {
        TURN_SPEED = 0.3;
        Direction readBlueRotateDirection;
        Direction readRedRotateDirection;

        if (beaconColorToPush == DetectedColor.Blue) {
            //ToDo: assume beacon detect sensor is on the right of robot
            //if sensor sees blue, turn robot left
            readBlueRotateDirection = Direction.Left;
            //if sensor sees red beacon, turn robot right.
            readRedRotateDirection = Direction.Right;
        } else {
            readBlueRotateDirection = Direction.Right;
            readRedRotateDirection = Direction.Left;
        }

        if (detectedBeaconColor(robot.beaconColorSensor) == DetectedColor.Blue) {
            turnRobot1Wheel(0.35, readBlueRotateDirection, 30, 1);
            if (readBlueRotateDirection == Direction.Left) freeMotorDrive(0.6, -0.1);
            else
                freeMotorDrive(-0.1, 0.6); //ToDo: also move the other side forward, need to give a little back force.  otherwise, the robot may not be able to move.
        } else if (detectedBeaconColor(robot.beaconColorSensor) == DetectedColor.Red) {
            turnRobot1Wheel(0.35, readRedRotateDirection, 30, 1);
            if (readRedRotateDirection == Direction.Right) freeMotorDrive(-0.10, 0.60);
            else freeMotorDrive(0.6, -0.1);
        }
        sleep(700); //push for some time
        stopMotor();
    }

    public void pushFrontBeaconSimpleRed() {

        Log.i("Test Push beacon", "pushFrontBeaconSimpleRed");

        DetectedColor beaconColor = detectedBeaconColor(robot.beaconColorSensor);

        Log.i(this.getClass().getSimpleName(), beaconColor.name());

        robot.leftMotor.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
        robot.rightMotor.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
        sleep(20);
        if (beaconColor == DetectedColor.Blue) {

            robot.leftMotor.setPower(0.3);
            robot.rightMotor.setPower(0.0);

            sleep(1000);

            stopMotor();
        } else if (beaconColor == DetectedColor.Red) {
            robot.leftMotor.setPower(0.0);
            robot.rightMotor.setPower(0.3);

            sleep(1000);

            stopMotor();
        } else {
            Log.i(this.getClass().getSimpleName(), "No Color Detected ...");
        }

        sleep(100);
        stopMotor();
    }

    public void pushFrontBeaconSimpleBlue() {

        Log.i("Test Push beacon", "pushFrontBeaconSimpleRed");

        DetectedColor beaconColor = detectedBeaconColor(robot.beaconColorSensor);
        Log.i(this.getClass().getSimpleName(), beaconColor.name());

        robot.leftMotor.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
        robot.rightMotor.setMode(DcMotor.RunMode.RUN_WITHOUT_ENCODER);
        sleep(20);
        if (beaconColor == DetectedColor.Blue) {

            robot.leftMotor.setPower(0.0);
            robot.rightMotor.setPower(0.3);

            sleep(1500);

            stopMotor();
        } else if (beaconColor == DetectedColor.Red) {
            robot.leftMotor.setPower(0.3);
            robot.rightMotor.setPower(0.0);

            sleep(1500);

            stopMotor();
        } else {
            Log.i(this.getClass().getSimpleName(), "No Color Detected ...");
        }

        sleep(500);
        stopMotor();
    }


    public void pushBallAfterHit1stBeacon(boolean isRedTeam) {
        DRIVE_SPEED = 0.25;
        encoderDrive(DRIVE_SPEED, -50, -50, 7);
        encoderDrive(DRIVE_SPEED, 2, 2, 2);
        turnRobot1Wheel(0.2, isRedTeam ? Direction.Left : Direction.Right, 45, 5);
        //encoderDrive(DRIVE_SPEED,-7,-7,5);
        //turnRobot(0.2, isRedTeam?Direction.Right:Direction.Left,40,5); //after push beacon, robot move back
    }

    enum Direction {Left, Right}

    enum DetectedColor {Blue, Red, Black, White}

    public void setupVuforia() {

        // Setup parameters to create localizer

        params = new VuforiaLocalizer.Parameters(R.id.cameraMonitorViewId);

        params.vuforiaLicenseKey = "Aa96UGf/////AAAAGcKOfzCYjkCzoSoNIzMvr8JRzY5qFtNcAcFmzlil1nQBvum1Ny6PlTkJ0HG6wazv53CSNd9oduRnGR457STwMjGDzgTdsg3dPW9aZZfaLpeU3JvNxJG0j3dtksf33TS0/x1wbQZJ91rJ+KEdei4InW13KWZGC3XwJHApkha2fpj6JbckRVJxzWMykbZ3IjlcZ0XlmXZR2vn0lFrdzLHUQctW0pHvJ2h4CRl11qgfnhVJF20gDLNhU7zSUfo6Sr9mByItdfSeDilWdy3oDEWr26wiUJUaRdMv+CYbTL81LH+Pxxz367xmrj+LwO1OLBAAG9yANvsQaK9YnfjO6gWDNtdSJ31SfNOrHT0KfOZ4ndzE"; // Insert your own key here

        params.cameraDirection = VuforiaLocalizer.CameraDirection.BACK;

        params.cameraMonitorFeedback = VuforiaLocalizer.Parameters.CameraMonitorFeedback.AXES;

        myVuforiaLocalizer = ClassFactory.createVuforiaLocalizer(params);

    }


    public void setupTarget(int targetNumber) {

        //Load targets//

        String targetNames[] = {"wheels", "tools", "legos", "gears"};

        beaconTargets = myVuforiaLocalizer.loadTrackablesFromAsset("FTC_2016-17");

        Vuforia.setHint(HINT.HINT_MAX_SIMULTANEOUS_IMAGE_TARGETS, 1);  //Just track 1 image on the left//


        // Setup the target to be tracked

        singleBeaconTarget = beaconTargets.get(targetNumber); // 3 corresponds to gears

        singleBeaconTarget.setName(targetNames[targetNumber]);


        //ToDo: Put target location in array, based on targetNumber, use the right parameters

        final float beaconTargetLocationXmm = -playFieldWidthInch * 25.4f / 2;
        final float beaconTargetLocationYmm = -playFieldWidthInch * 25.4f / 12;
        final float beaconTargetLocationZmm = 0;
        final float beaconTargetRotationUdeg = 90;
        final float beaconTargetRotationVdeg = 0;
        final float beaconTargetRotationWdeg = 90;


        OpenGLMatrix beaconTargetLocation = createMatrix(beaconTargetLocationXmm,

                beaconTargetLocationYmm,

                beaconTargetLocationZmm,

                beaconTargetRotationUdeg,

                beaconTargetRotationVdeg,

                beaconTargetRotationWdeg);


        singleBeaconTarget.setLocation(beaconTargetLocation);

    }


    public void setupPhone() {

        /* Set phone location on robot

        Robot origin at the middle of robot

        X: to the left

        Y: to the front

        Z: vertical up

        Looking into origin along any axes, CCW is positive angle.



        Phone original position is laying flat on robot, screen facing up, short side parallel to robot front edge, long side parallel to robot right edge.

        Phone location: Middle, Front of robot, screen facing inside, landscape.

        Movement from original position is:

            move +Y half robot length

            flip 90 degree around X

        Phone location using mm.

         */

        OpenGLMatrix phoneLocation = createMatrix(0, robotWidthInch*25.4f/2, 0, 90, -90, 0);


        // Setup listener and inform it of phone information

        listener = (VuforiaTrackableDefaultListener) singleBeaconTarget.getListener();

        listener.setPhoneInformation(phoneLocation, params.cameraDirection);

    }


    public double getAngleToTurn() {

        //Based on Vuforia reading, return the angles need to turn to align to center of beacon target.

        double beaconTargetAngle;

        //feedback if phone can see the target

        telemetry.addData(singleBeaconTarget.getName(), ((VuforiaTrackableDefaultListener) singleBeaconTarget.getListener()).isVisible() ? "Visible" : "Not Visible");    //

        OpenGLMatrix robotLocation = ((VuforiaTrackableDefaultListener) singleBeaconTarget.getListener()).getUpdatedRobotLocation();

        if (robotLocation != null) {

            telemetry.addData("Pos", format(robotLocation)); //This should be a string containing xyz and uvw data.  Need to parse to get the data.



            /*Another method is to use vector, which should contain xyz data

            use getPose method,which returns data in phone's coordinate.  If phone in landscape mode:

            lef/right: Y value change.  0 is aligned.

            up/down: X value change.

            forward/backward: z valude change.  Always negative because +Z is pointing away from screen (to user direction)

             */

            VectorF beaconTargetVector = ((VuforiaTrackableDefaultListener) singleBeaconTarget.getListener()).getPose().getTranslation();

            //Phone in landscape mode, use Y and Z to calculate target to phone angle

            //If Phone in portrait mode, use X and Z to calculate angles to turn


            beaconTargetAngle = Math.toDegrees(Math.atan2(beaconTargetVector.get(1), Math.abs(beaconTargetVector.get(2)))); //use abs to make Z positive.  Then the calculated angle will be centered around 0 degree.


            telemetry.addLine(singleBeaconTarget.getName() + "Degrees to Turn:" + beaconTargetAngle); //show the angle calculated by XYZ coordinate

        } else {

            telemetry.addData("Pos", "Unknown");

            telemetry.addLine("Keep current direction"); //ToDo: This may not be right as if don't turn, may never find target.

            beaconTargetAngle = 45;

        }

        telemetry.update();

        return (beaconTargetAngle);

    }


    public OpenGLMatrix createMatrix(float x, float y, float z, float u, float v, float w) {

        return OpenGLMatrix.translation(x, y, z).multiplied(Orientation.getRotationMatrix(

                AxesReference.EXTRINSIC, AxesOrder.XYZ, AngleUnit.DEGREES, u, v, w));

    }


    String format(OpenGLMatrix transformationMatrix) {

        return transformationMatrix.formatAsTransform();

    }

}