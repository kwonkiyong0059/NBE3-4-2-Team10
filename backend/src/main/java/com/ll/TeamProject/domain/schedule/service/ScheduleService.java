package com.ll.TeamProject.domain.schedule.service;

import com.ll.TeamProject.domain.calendar.entity.Calendar;
import com.ll.TeamProject.domain.calendar.repository.CalendarRepository;
import com.ll.TeamProject.domain.schedule.dto.ScheduleRequestDto;
import com.ll.TeamProject.domain.schedule.dto.ScheduleResponseDto;
import com.ll.TeamProject.domain.schedule.entity.Schedule;
import com.ll.TeamProject.domain.schedule.mapper.ScheduleMapper;
import com.ll.TeamProject.domain.schedule.repository.ScheduleRepository;
import com.ll.TeamProject.domain.user.entity.SiteUser;
import com.ll.TeamProject.global.exceptions.ServiceException;
import com.ll.TeamProject.global.userContext.UserContext;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ScheduleService {

    private final ScheduleRepository scheduleRepository;
    private final CalendarRepository calendarRepository;
    private final UserContext userContext;
    private final ScheduleMapper scheduleMapper;

    // 일정 생성
    public ScheduleResponseDto createSchedule(Long calendarId, ScheduleRequestDto scheduleRequestDto) {
        SiteUser user = getAuthenticatedUser();
        Calendar calendar = validateCalendarOwner(calendarId, user);
        Schedule schedule = new Schedule(
                calendar,
                scheduleRequestDto.title(),
                scheduleRequestDto.description(),
                user,
                scheduleRequestDto.startTime(),
                scheduleRequestDto.endTime(),
                scheduleRequestDto.location()
        );
        return scheduleMapper.toDto(scheduleRepository.save(schedule));
    }

    // 일정 수정
    public ScheduleResponseDto updateSchedule(Long calendarId, Long scheduleId, ScheduleRequestDto scheduleRequestDto) {
        SiteUser user = getAuthenticatedUser();
        Schedule schedule = validateScheduleOwnership(calendarId, scheduleId, user, "수정");
        schedule.update(
                scheduleRequestDto.title(),
                scheduleRequestDto.description(),
                scheduleRequestDto.startTime(),
                scheduleRequestDto.endTime(),
                scheduleRequestDto.location()
        );


        return scheduleMapper.toDto(schedule);
    }

    // 일정 삭제
    public void deleteSchedule(Long calendarId, Long scheduleId) {
        SiteUser user = getAuthenticatedUser();
        validateScheduleOwnership(calendarId, scheduleId, user, "삭제");
        scheduleRepository.deleteById(scheduleId);
    }

    // 지정 기간의 일정 목록 조회
    public List<ScheduleResponseDto> getSchedules(Long calendarId, LocalDate startDate, LocalDate endDate) {
        SiteUser user = getAuthenticatedUser();
        validateCalendarOwner(calendarId, user);
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);
        return scheduleRepository.findSchedulesByCalendarAndDateRange(calendarId, startDateTime, endDateTime)
                .stream().map(scheduleMapper::toDto).toList();
    }

    // 하루 일정 조회
    public List<ScheduleResponseDto> getDailySchedules(Long calendarId, LocalDate date) {
        SiteUser user = getAuthenticatedUser();
        validateCalendarOwner(calendarId, user);
        LocalDateTime[] range = getDayRange(date);
        return scheduleRepository.findSchedulesByCalendarAndDateRange(calendarId, range[0], range[1])
                .stream().map(scheduleMapper::toDto).toList();
    }

    // 주별 일정 조회 (일요일 ~ 토요일)
    public List<ScheduleResponseDto> getWeeklySchedules(Long calendarId, LocalDate date) {
        SiteUser user = getAuthenticatedUser();
        validateCalendarOwner(calendarId, user);
        LocalDateTime[] range = getWeekRange(date);
        return scheduleRepository.findSchedulesByCalendarAndDateRange(calendarId, range[0], range[1])
                .stream().map(scheduleMapper::toDto).toList();
    }

    // 월별 일정 조회 (해당 월의 1일 ~ 말일)
    public List<ScheduleResponseDto> getMonthlySchedules(Long calendarId, LocalDate date) {
        SiteUser user = getAuthenticatedUser();
        validateCalendarOwner(calendarId, user);
        LocalDateTime[] range = getMonthRange(date);
        return scheduleRepository.findSchedulesByCalendarAndDateRange(calendarId, range[0], range[1])
                .stream().map(scheduleMapper::toDto).toList();
    }

    // 특정 일정 조회 (캘린더 소유자 검증 포함)
    public ScheduleResponseDto getScheduleById(Long calendarId, Long scheduleId) {
        SiteUser user = getAuthenticatedUser();
        validateCalendarOwner(calendarId, user);
        Schedule schedule = getScheduleByIdOrThrow(scheduleId);
        if (!schedule.getCalendar().getId().equals(calendarId)) {
            throw new ServiceException("400", "해당 일정은 요청한 캘린더에 속하지 않습니다.");
        }
        return scheduleMapper.toDto(schedule);
    }

    // --- 내부 헬퍼 메서드 ---

    // 캘린더 존재 및 소유자 검증
    private Calendar validateCalendarOwner(Long calendarId, SiteUser user) {
        Calendar calendar = getCalendarByIdOrThrow(calendarId);
        checkCalendarOwnership(calendar, user);
        return calendar;
    }

    // 일정 존재 확인
    private Schedule getScheduleByIdOrThrow(Long scheduleId) {
        return scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ServiceException("404", "해당 일정을 찾을 수 없습니다."));
    }

    // 캘린더 존재 확인
    private Calendar getCalendarByIdOrThrow(Long calendarId) {
        return calendarRepository.findById(calendarId)
                .orElseThrow(() -> new ServiceException("404", "해당 캘린더를 찾을 수 없습니다."));
    }
    // 캘린더 소유자 검증 (캘린더 존재 확인 후 호출)
    private void checkCalendarOwnership(Calendar calendar, SiteUser user) {
        if (!calendar.getUser().getId().equals(user.getId())) {
            throw new ServiceException("403", "캘린더 소유자만 접근할 수 있습니다.");
        }
    }

    // 일정 수정/삭제 시 캘린더 및 일정 소유자 검증 (action: "수정" 또는 "삭제")
    private Schedule validateScheduleOwnership(Long calendarId, Long scheduleId, SiteUser user, String action) {
        validateCalendarOwner(calendarId, user);
        Schedule schedule = getScheduleByIdOrThrow(scheduleId);
        if (!schedule.getCalendar().getId().equals(calendarId)) {
            throw new ServiceException("400", "해당 일정은 요청한 캘린더에 속하지 않습니다.");
        }
        if (!schedule.getUser().getId().equals(user.getId())) {
            throw new ServiceException("403", "일정을 " + action + "할 권한이 없습니다.");
        }
        return schedule;
    }

    // 하루 날짜 범위 계산 (시작: 00:00, 종료: 23:59:59.999)
    private LocalDateTime[] getDayRange(LocalDate date) {
        return new LocalDateTime[]{date.atStartOfDay(), date.atTime(LocalTime.MAX)};
    }

    // 주간 날짜 범위 계산 (일요일 ~ 토요일)
    private LocalDateTime[] getWeekRange(LocalDate date) {
        LocalDate startOfWeek = date.with(DayOfWeek.SUNDAY);
        LocalDate endOfWeek = date.with(DayOfWeek.SATURDAY);
        return new LocalDateTime[]{startOfWeek.atStartOfDay(), endOfWeek.atTime(LocalTime.MAX)};
    }

    // 월간 날짜 범위 계산 (해당 월의 1일 ~ 말일)
    private LocalDateTime[] getMonthRange(LocalDate date) {
        LocalDate firstDay = date.withDayOfMonth(1);
        LocalDate lastDay = date.withDayOfMonth(date.lengthOfMonth());
        return new LocalDateTime[]{firstDay.atStartOfDay(), lastDay.atTime(LocalTime.MAX)};
    }

    // 현재 인증된 사용자 조회 (UserContext 통한 조회)
    public SiteUser getAuthenticatedUser() {
        return userContext.findActor()
                .orElseThrow(() -> new ServiceException("401", "로그인을 먼저 해주세요!"));
    }
}
