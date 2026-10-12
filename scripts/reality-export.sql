BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;
SET LOCAL statement_timeout = '90s';
WITH selected_activities AS (
    SELECT a.* FROM public.activities a WHERE __OWNER_CONDITION__
), selected_sessions AS (
    SELECT s.* FROM public.session s JOIN selected_activities a ON a.id=s.activity_id
), selected_breaks AS (
    SELECT b.* FROM public.session_break b JOIN selected_sessions s ON s.id=b.session_id
), scheduled_dates AS (
    SELECT a.id AS activity_id, d::date AS day
    FROM selected_activities a
    CROSS JOIN LATERAL generate_series(
        a.start_date::timestamp,
        ((CURRENT_TIMESTAMP AT TIME ZONE '__SOURCE_TIMEZONE__')::date - 1)::timestamp,
        interval '1 day'
    ) d
    WHERE a.active IS TRUE AND EXISTS (
        SELECT 1 FROM public.activity_scheduled_days sd
        WHERE sd.activity_id=a.id AND sd.day_of_week=(ARRAY['MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY','SUNDAY'])[extract(isodow FROM d)::int]
    )
), daily_totals AS (
    SELECT d.activity_id,d.day,
        COALESCE((SELECT SUM(GREATEST(0,
            floor(extract(epoch FROM (LEAST(s.end_time,d.day+interval '1 day')-GREATEST(s.start_time,d.day::timestamp))))::bigint
            - COALESCE((SELECT SUM(GREATEST(0,floor(extract(epoch FROM (
                LEAST(COALESCE(b.end_time,s.end_time),s.end_time,d.day+interval '1 day')
                -GREATEST(b.start_time,s.start_time,d.day::timestamp)
            )))::bigint)) FROM selected_breaks b WHERE b.session_id=s.id),0)
        )) FROM selected_sessions s WHERE s.activity_id=d.activity_id AND s.end_time IS NOT NULL
            AND s.start_time < d.day+interval '1 day' AND s.end_time > d.day::timestamp),0) AS seconds
    FROM scheduled_dates d
), completed_dates AS (
    SELECT d.*, d.seconds >= a.minimum_duration::bigint*60 AS completed
    FROM daily_totals d JOIN selected_activities a ON a.id=d.activity_id
), streak_data AS (
    SELECT a.id,a.name,((CURRENT_TIMESTAMP AT TIME ZONE '__SOURCE_TIMEZONE__')::date - 1) AS evaluated,
        (SELECT count(*) FROM completed_dates d WHERE d.activity_id=a.id AND d.completed
            AND d.day > COALESCE((SELECT max(f.day) FROM completed_dates f WHERE f.activity_id=a.id AND NOT f.completed),'-infinity'::date)) AS streak
    FROM selected_activities a WHERE a.active IS TRUE
)
SELECT jsonb_build_object(
    'app','Reality','schemaVersion',1,
    'exportedAt',to_char(CURRENT_TIMESTAMP AT TIME ZONE 'UTC','YYYY-MM-DD"T"HH24:MI:SS.US"Z"'),
    'timeZone','__SOURCE_TIMEZONE__','source','spring-boot-postgresql',
    'activities',COALESCE((SELECT jsonb_agg(jsonb_build_object(
        'id',a.id,'name',a.name,'minimumDuration',a.minimum_duration,'category',a.category,'active',a.active,
        'startDate',a.start_date,'createdAt',a.created_at,'updatedAt',a.updated_at,
        'scheduledDays',COALESCE((SELECT jsonb_agg(sd.day_of_week ORDER BY sd.day_of_week) FROM public.activity_scheduled_days sd WHERE sd.activity_id=a.id),'[]'::jsonb)
    ) ORDER BY a.id) FROM selected_activities a),'[]'::jsonb),
    'sessions',COALESCE((SELECT jsonb_agg(jsonb_build_object(
        'id',s.id,'activityId',s.activity_id,'startTime',s.start_time,'endTime',s.end_time,'duration',s.duration
    ) ORDER BY s.id) FROM selected_sessions s),'[]'::jsonb),
    'breaks',COALESCE((SELECT jsonb_agg(jsonb_build_object(
        'id',b.id,'sessionId',b.session_id,'startTime',b.start_time,'endTime',b.end_time,'duration',b.duration,'description',b.description
    ) ORDER BY b.id) FROM selected_breaks b),'[]'::jsonb),
    'streaks',COALESCE((SELECT jsonb_agg(jsonb_build_object(
        'activityId',s.id,'activityName',s.name,'currentStreak',s.streak,'lastEvaluatedDate',s.evaluated
    ) ORDER BY s.id) FROM streak_data s),'[]'::jsonb)
);
COMMIT;
