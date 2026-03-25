package pl.piomin.services.employee.controller;

import java.util.List;
import java.util.NoSuchElementException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import pl.piomin.services.employee.model.Employee;
import pl.piomin.services.employee.model.ErrorCode;
import pl.piomin.services.employee.repository.EmployeeRepository;

import static net.logstash.logback.argument.StructuredArguments.kv;

@RestController
public class EmployeeController {

	private static final Logger LOGGER = LoggerFactory.getLogger(EmployeeController.class);

	@Autowired
	EmployeeRepository repository;

	@PostMapping("/")
	public Employee add(@RequestBody Employee employee) {
		LOGGER.debug("Employee add request received", kv("organizationId", employee.getOrganizationId()), kv("departmentId", employee.getDepartmentId()));
		try {
			Employee saved = repository.add(employee);
			LOGGER.info("Employee added", kv("employeeId", saved.getId()), kv("organizationId", saved.getOrganizationId()), kv("departmentId", saved.getDepartmentId()));
			return saved;
		} catch (Exception e) {
			LOGGER.error("Failed to add employee", kv("errorCode", ErrorCode.INTERNAL_ERROR.getCode()), kv("organizationId", employee.getOrganizationId()), e);
			throw e;
		}
	}

	@GetMapping("/{id}")
	public Employee findById(@PathVariable("id") Long id) {
		LOGGER.debug("Employee findById request received", kv("employeeId", id));
		try {
			Employee employee = repository.findById(id);
			LOGGER.info("Employee found", kv("employeeId", id));
			return employee;
		} catch (NoSuchElementException e) {
			LOGGER.error("Employee not found", kv("errorCode", ErrorCode.EMPLOYEE_NOT_FOUND.getCode()), kv("employeeId", id), e);
			throw e;
		}
	}

	@GetMapping("/")
	public List<Employee> findAll() {
		LOGGER.debug("Employee findAll request received");
		List<Employee> employees = repository.findAll();
		LOGGER.info("Employees fetched", kv("count", employees.size()));
		return employees;
	}

	@GetMapping("/department/{departmentId}")
	public List<Employee> findByDepartment(@PathVariable("departmentId") Long departmentId) {
		LOGGER.debug("Employee findByDepartment request received", kv("departmentId", departmentId));
		List<Employee> employees = repository.findByDepartment(departmentId);
		LOGGER.info("Employees fetched by department", kv("departmentId", departmentId), kv("count", employees.size()));
		return employees;
	}

	@GetMapping("/organization/{organizationId}")
	public List<Employee> findByOrganization(@PathVariable("organizationId") Long organizationId) {
		LOGGER.debug("Employee findByOrganization request received", kv("organizationId", organizationId));
		List<Employee> employees = repository.findByOrganization(organizationId);
		LOGGER.info("Employees fetched by organization", kv("organizationId", organizationId), kv("count", employees.size()));
		return employees;
	}

}
