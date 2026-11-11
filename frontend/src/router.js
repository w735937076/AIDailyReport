import { createRouter, createWebHistory } from 'vue-router'
import Dashboard from './views/Dashboard.vue'
import Projects from './views/Projects.vue'
import AiSettings from './views/AiSettings.vue'
import Scheduler from './views/Scheduler.vue'
import Generate from './views/Generate.vue'
import WorkItems from './views/WorkItems.vue'
import Performance from './views/Performance.vue'
import Interview from './views/Interview.vue'

export default createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'dashboard', component: Dashboard },
    { path: '/work-items', name: 'work-items', component: WorkItems },
    { path: '/projects', name: 'projects', component: Projects },
    { path: '/ai', name: 'ai', component: AiSettings },
    { path: '/scheduler', name: 'scheduler', component: Scheduler },
    { path: '/generate', name: 'generate', component: Generate },
    { path: '/performance', name: 'performance', component: Performance },
    { path: '/interview', name: 'interview', component: Interview },
  ],
})
