# a LEGO inventory app

![Screenshot](./assets/screenshot.png)

This repo is a work in progress.

## Parts

**index**

The application allowing for sets to be entered. It'll then fetch the parts & auxiliary information from rebrickable and stores it.

**microcontroller_cpp**

The bare mininmum of C++ code for an ESP32-Wrover to allow for controlling servos and stepper motors via a JSON API.

**orchestrator**

The orchestrator talks to the controllers to control servos and stepper motors to capture pictures of parts going past and select in which buckets they'll be sorted. 
