create table author
(
    id     serial primary key,
    fio    varchar(255)  not null,
    creation_time  timestamp  not null
);

comment on column author.fio is 'ФИО';

comment on column author.creation_time is 'Дата создания';

alter table budget add column author_id int references author(id) on delete set null;