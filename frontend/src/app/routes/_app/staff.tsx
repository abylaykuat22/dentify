import { createFileRoute, Outlet } from '@tanstack/react-router'

import { guardSection, sectionHead } from '@/app/guards'
import { SectionError } from '@/app/shell/SectionError'

// Раздел «staff»: право проверяется в beforeLoad, «нет доступа» рисует errorComponent
// внутри каркаса (заголовок вкладки — sectionHead); вкладки — дочерние маршруты в папке staff/.
export const Route = createFileRoute('/_app/staff')({
  // Компонент лейаута — просто Outlet (он уже в начальной загрузке): отдельный чанк ради него — лишний
  // запрос в цепочке первого экрана (PLT-22). В чанк уходит только «нет доступа».
  codeSplitGroupings: [['errorComponent']],
  beforeLoad: guardSection('staff'),
  head: sectionHead,
  errorComponent: SectionError,
  component: Outlet,
})
