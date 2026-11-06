package com.training.trackplanner.data

import android.database.sqlite.SQLiteFullException
import android.database.sqlite.SQLiteException
import java.io.IOException

internal enum class ProgramApplyFailureReason {
    PROGRAM_MISSING, NO_EXERCISES, INVALID_DATE, INVALID_STRUCTURE
}

internal class ProgramApplyException(val reason: ProgramApplyFailureReason) :
    IllegalStateException(reason.name)

/** Human-facing messages; internal exception details must never leak into UI. */
internal object ProgramApplyErrorMessage {
    fun from(error: Throwable): String {
        val reason = generateSequence(error) { it.cause }.filterIsInstance<ProgramApplyException>().firstOrNull()?.reason
        return when (reason) {
            ProgramApplyFailureReason.PROGRAM_MISSING ->
                "적용하려던 프로그램을 찾지 못했습니다. 계획 목록을 다시 열어 주세요."
            ProgramApplyFailureReason.NO_EXERCISES ->
                "프로그램에 배치된 운동이 없어 날짜에 적용할 수 없습니다. 운동을 추가한 뒤 다시 시도해 주세요."
            ProgramApplyFailureReason.INVALID_DATE ->
                "시작 날짜 또는 프로그램 기간이 올바르지 않습니다. 날짜를 다시 선택해 주세요."
            ProgramApplyFailureReason.INVALID_STRUCTURE ->
                "운동의 주차·요일 설정이 프로그램 기간과 맞지 않습니다. 프로그램을 수정한 뒤 다시 시도해 주세요."
            null -> when {
                generateSequence(error) { it.cause }.any { it is SQLiteFullException } ->
                    "휴대폰 저장 공간이 부족하여 계획을 저장하지 못했습니다. 공간을 확보한 뒤 다시 시도해 주세요."
                generateSequence(error) { it.cause }.any { it is IOException } ->
                    "기록을 보호하기 위한 복구본을 저장하지 못했습니다. 저장 공간을 확인한 뒤 다시 시도해 주세요."
                generateSequence(error) { it.cause }.any { it is SQLiteException } ->
                    "운동 기록을 저장하는 중 데이터베이스 오류가 발생했습니다. 기존 기록을 확인하고 다시 시도해 주세요."
                else ->
                    "계획을 적용하지 못했습니다. 기존 기록은 유지됩니다. 앱을 다시 열어 시도해 주세요."
            }
        }
    }
}
