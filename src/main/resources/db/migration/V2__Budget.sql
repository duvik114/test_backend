-- Миграция для изменения значения поля 'type' в таблице 'budget_types'
begin;

update budget
set type = 'Расход'
where type = 'Комиссия';

commit;