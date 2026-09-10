package com.pakarai.alarme.ui.challenge

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.ScreenRotation

/**
 * Modos de desafio para desligar o alarme.
 * Guardados como string em [com.pakarai.alarme.data.AlarmEntity.challengeMode].
 */
enum class ChallengeMode(
    val key: String,
    val label: String,
    val shortCaption: String,
    val hint: String,
    val icon: ImageVector,
) {
    MATH(
        "math",
        "MATEMÁTICA",
        "Contas rápidas",
        "Resolva as contas corretamente. Errou, vem outra na hora.",
        Icons.Filled.Calculate
    ),
    MEMORY(
        "memory",
        "MEMÓRIA",
        "Repita na ordem",
        "Memorize a sequência de ícones e repita na ordem exata.",
        Icons.Filled.Memory
    ),
    SHAKE(
        "shake",
        "AGITAR",
        "Chacoalhe",
        "Agite o celular energicamente até zerar a contagem.",
        Icons.Filled.FitnessCenter
    ),
    STEPS(
        "steps",
        "ANDAR",
        "Levante e ande",
        "Levante da cama e ande com o celular no bolso até completar os passos.",
        Icons.Filled.DirectionsWalk
    ),
    QR(
        "qr",
        "QR CODE",
        "Escaneie",
        "Escaneie com a câmera o QR Code que contém o segredo definido no editor.",
        Icons.Filled.QrCodeScanner
    ),
    TYPE(
        "type",
        "DIGITAR",
        "Digite a palavra",
        "Digite a palavra que aparece na tela. Só acorda quem está acordado.",
        Icons.Filled.Keyboard
    ),
    SPIN(
        "spin",
        "GIRAR",
        "Gire o celular",
        "Gire o celular no sentido pedido até alinhar o alvo.",
        Icons.Filled.ScreenRotation
    ),
    OBJECT(
        "object",
        "OBJETO",
        "Fotografe o objeto",
        "Cadastre a foto de um objeto no editor e, na hora do alarme, fotografe o mesmo objeto pra desligar.",
        Icons.Filled.Category
    );

    companion object {
        fun fromKey(key: String?): ChallengeMode =
            entries.firstOrNull { it.key == key } ?: MATH

        /** Modos onde o "Nº de rodadas" faz sentido (cada rodada é um novo desafio). */
        fun supportsRounds(mode: ChallengeMode): Boolean = when (mode) {
            MATH, MEMORY, TYPE, OBJECT -> true
            else -> false
        }
    }
}