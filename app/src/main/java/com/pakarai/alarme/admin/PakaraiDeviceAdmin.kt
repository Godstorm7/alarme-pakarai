package com.pakarai.alarme.admin

import android.app.admin.DeviceAdminReceiver

/**
 * Admin de dispositivo usado SÓ pra dificultar desinstalar o alarme por engano.
 * Não aplica nenhuma política de senha/bloqueio — a única policy declarada é
 * force-lock (opcional). Pra remover, o usuário desativa em Segurança → Apps de administração.
 */
class PakaraiDeviceAdmin : DeviceAdminReceiver()
