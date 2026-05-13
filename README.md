# droidvisor

![License: AGPL v3](https://img.shields.io/badge/License-AGPL%20v3-blue.svg)
![Android: 13+](https://img.shields.io/badge/Android-13%2B-green.svg)
![Status: MVP Planning](https://img.shields.io/badge/Status-MVP%20Planning-orange.svg)

## Overview

droidvisor is a virtualization application platform built on Android AVF (Android Virtualization Framework). Its goal is to transform Android devices into virtualization terminals capable of running complete Linux distributions and Docker containers -- turning your phone into a portable cloud workstation.

## Core Features

- **Debian VM Runtime**: Run a complete Debian virtual machine on Android, powered by the AVF `VirtualMachineManager` API. No root, no flashing required.
- **Docker Support**: Run Docker Engine inside the VM, with container lifecycle management via Vsock proxy API -- bridging mobile and server technology stacks.
- **Native Android UI**: Material 3 interface built with Jetpack Compose, featuring VM status panel, terminal interaction, and Docker container management -- deeply integrated with the Android system.

## Project Status

The project is currently in the **MVP planning phase**. Core technical analysis and feature specifications have been completed. Implementation of the MVP is the next milestone. See the [MVP Feature Specification](./docs/core/mvp-definition.md) for the detailed roadmap.

## Technical Architecture

droidvisor adopts a layered architecture from the underlying AVF virtualization to the top-level Jetpack Compose UI:

```
Virtualization Layer  │  Android AVF (pKVM hypervisor)
VM Communication      │  Vsock (Virtual Socket) shared memory channel
UI Framework          │  Jetpack Compose + Material 3
Container Engine      │  Docker Engine (running inside Debian VM)
```

For an in-depth technical analysis of AVF capabilities, API lifecycle, Vsock communication, and platform comparisons, see the [AVF Analysis Document](./docs/core/avf-analysis.md).

## Quick Start

> Detailed build and run instructions will be added during the MVP implementation phase.

### Prerequisites

- Android 13+ (API 33+) device
- Device must support AVF (Android Virtualization Framework)
- Android Studio Hedgehog (2023.1.1) or later

## Documentation

| Document | Description |
|----------|-------------|
| [Android AVF In-Depth Analysis](./docs/core/avf-analysis.md) | AVF technical capabilities, pKVM architecture, VirtualMachineManager API lifecycle, Vsock communication, platform comparison (Firecracker, CrosVM, WSL2, Kata Containers), and development roadmap |
| [MVP Feature Specification](./docs/core/mvp-definition.md) | MVP feature definitions (Debian VM, Docker Engine, Basic UI), technical architecture, UI design overview, milestone planning (M1-M5), technical constraints and risk analysis |

## License

This project is licensed under the [GNU Affero General Public License v3.0](./LICENSE) -- a strong copyleft license that requires making the source code available to users who interact with the software over a network.