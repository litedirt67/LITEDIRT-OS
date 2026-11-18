package com.example.engine

import com.example.data.model.VirtualInstance
import com.example.data.model.VirtualOsArchitectureMode
import java.io.File

object ContainerEngineConfig {

    fun buildCommandPreview(instance: VirtualInstance, rootfsDir: File): List<String> {
        return when (instance.engineMode) {
            VirtualOsArchitectureMode.PROOT_CONTAINER -> listOf(
                "proot",
                "--kill-on-exit",
                "-0",
                "-r", rootfsDir.absolutePath,
                "-b", "/dev",
                "-b", "/proc",
                "-b", "/sys",
                "-b", "${rootfsDir.absolutePath}/sdcard/VirtualShared:/sdcard/VirtualShared",
                "-w", "/data/local/tmp",
                "/system/bin/env",
                "ANDROID_DATA=/data",
                "ANDROID_ROOT=/system",
                "VIRTUALOS_ROOT=${if (instance.rootEnabled) "1" else "0"}",
                "VIRTUALOS_VNC_PORT=${instance.vncPort}",
                "/system/bin/sh"
            )

            VirtualOsArchitectureMode.QEMU_SYSTEM -> listOf(
                "qemu-system-aarch64",
                "-machine", "virt",
                "-cpu", "cortex-a57",
                "-smp", instance.cpuCores.toString(),
                "-m", "${instance.ramLimitMb}M",
                "-drive", "file=${rootfsDir.parentFile?.absolutePath ?: rootfsDir.absolutePath}/android10.qcow2,if=virtio",
                "-netdev", if (instance.networkMode.name == "NAT") "user,id=net0,hostfwd=tcp::${instance.adbPort}-:5555" else "tap,id=net0",
                "-device", "virtio-net-pci,netdev=net0",
                "-vnc", "127.0.0.1:${instance.vncPort - 5900}"
            )
        }
    }

    fun getArchitectureRationale(): String = buildString {
        appendLine("Mengapa LitedirtVM memprioritaskan PRoot/Chroot Container dibanding QEMU penuh?")
        appendLine("1. PRoot Container berjalan langsung di atas kernel Linux host menggunakan translasi syscall (ptrace/seccomp) tanpa emulasi instruksi CPU, sehingga performa mendekati native dan hemat baterai.")
        appendLine("2. QEMU tanpa akselerasi /dev/kvm di perangkat Android non-root harus melakukan emulasi TCG (software CPU translation) yang sangat lambat untuk menjalankan Android 10 ART.")
        appendLine("3. Batasan jujur pada Android 10+ non-root: chroot() & mount() asli butuh CAP_SYS_ADMIN (host root), SELinux melarang execve dari app data dir pada targetSdk >= 29, dan binder/SurfaceFlinger Android tidak bisa dipartisi penuh tanpa modul kernel atau hak istimewa sistem.")
    }
}
